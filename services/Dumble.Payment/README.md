# Dumble.Payment

Paymob integration: charges, refunds, withdrawals, cohort payouts, webhooks, reconciliation.

Source of truth: [`Payment-Service-Decisions.pdf`](../../../../Payment-Service-Decisions.pdf). Everything in this directory traces back to a numbered decision in that document.

## Boundary

- Payment is the **only** service that talks to Paymob (Decision 1.1). Subscription and Wallet call Payment for charges / refunds / withdrawals; Payment alone owns provider-specific code.
- Payment does **not** own subscription state, escrow, balances, or cohorts (Decision 1.2). It just executes movements: charges (money in), refunds (money back), payouts (money out), each as a standalone transaction with a caller-supplied reference.
- v1 callers: Subscription (charges + refunds), Wallet (withdrawals). Future: FitStore, Sessions.

## API surface (port 8183, context-path `/api`)

| Endpoint | Caller | Purpose |
|---|---|---|
| `POST /payment/charges` | Subscription | Charge a tokenised card / wallet (Decision 3.1) |
| `GET  /payment/charges/{id}` | Subscription, admin | Read charge state |
| `POST /payment/refunds` | Subscription | Process chargeback or admin force-refund (Decision 5.2) |
| `POST /payment/withdrawals` | Wallet | Move money out to user's bank / wallet (Decision 6.1) |
| `GET  /payment/withdrawals/by-caller-ref/{ref}` | Wallet (reaper) | Recover stuck rows on Wallet's side |
| `POST /payment/payouts` | Subscription | Cohort payout to seller's bank (Decision 6.2) |
| `GET  /payment/payouts/by-caller-ref/{ref}` | Subscription | Symmetric recovery lookup |
| `POST /payment/payment-methods/tokenize` | Frontend / Subscription | Save a tokenised card for future use (Decision 10.1) |
| `POST /payment/webhooks/paymob` | Paymob | Inbound webhook (HMAC-verified, Decision 4.1) |
| `GET  /admin/payment/recon` | Admin | Reconciliation report (Decision 7.3) |

## Provider abstraction

[`IPaymentProvider`](src/main/java/com/example/DumblePayment/provider/IPaymentProvider.java) (Decision 2.2) hides Paymob types from domain code. v1 ships [`PaymobProvider`](src/main/java/com/example/DumblePayment/provider/PaymobProvider.java); adding Stripe / Kashier later requires a new impl and zero domain changes.

The Paymob impl has two modes gated by `paymob.enabled`:
- **Real mode** — actual Paymob HTTP. v1 hardcodes EGP only; SDK wiring is intentionally minimal so future evolution is local.
- **Stub mode** — deterministic dev/test responses. Charges return `PENDING`, payouts return `PENDING`, refunds return `SUCCEEDED`. The HMAC verification path is still exercised when a signature is supplied (so misconfigured tests fail loudly).

## Webhook handling — the three rules

[`Decision 4.1 / 4.2 / 4.3`](../../../../Payment-Service-Decisions.pdf): verify, dedup, persist.

1. **Verify** — HMAC-SHA512 with constant-time compare in [`PaymobProvider.verifyWebhookSignature`](src/main/java/com/example/DumblePayment/provider/PaymobProvider.java).
2. **Dedup** — Paymob event id is the PK on `webhook_events`. A duplicate insert is caught and treated as success (so Paymob stops retrying) without re-running side effects.
3. **Two-phase ACK** — Phase 1 ([`WebhookService.receive`](src/main/java/com/example/DumblePayment/service/WebhookService.java)) verifies + dedups + ACKs in milliseconds. Phase 2 ([`WebhookProcessingJob`](src/main/java/com/example/DumblePayment/scheduler/WebhookProcessingJob.java)) drains pending rows and applies state changes + emits domain events.

## Concurrency + correctness

- **Idempotency-Key required** on every monetary write (Decision 3.2). Insert-first-with-PENDING via [`IdempotencyKeyStore`](src/main/java/com/example/DumblePayment/service/IdempotencyKeyStore.java) — concurrent peers serialize on the PK, get 409 if mid-flight, replay cached body if completed. 24h TTL with a nightly cleanup job.
- **Pessimistic locks** on `Charge` / `Payout` for the webhook async processor and any post-Paymob lifecycle update.
- **Outbox pattern** (Decision 9.4) for all outbound events. Raw bytes + `application/json` content-type so the Jackson converter doesn't re-encode the already-serialised payload.

## Security

- All internal endpoints require a system JWT (Decision 9.3) — `aud=payment`, ~60s TTL, `jti` logged for audit. Webhook + tokenize endpoints are exempt (HMAC and frontend-supplied opaque token, respectively).
- [`SystemSigningKey`](src/main/java/com/example/DumblePayment/security/SystemSigningKey.java) refuses `< 32`-byte keys outright (no zero-padding).
- [`StartupGuard`](src/main/java/com/example/DumblePayment/config/StartupGuard.java) refuses to boot when:
  - the service JWT signing key looks dev-shaped and no `dev`/`test`/`local` profile is active, or
  - the Paymob API key starts with the configured `prod-key-prefix` (default `live_`) and the active profile isn't `prod` (Decision 10.3 — "non-prod must not hit real Paymob").

## Schema

Single migration so far ([`V1__initial_schema.sql`](src/main/resources/db/migration/V1__initial_schema.sql)) covers:
- `charges`, `refunds`, `payouts`, `payment_method_tokens`
- `webhook_events`, `idempotency_keys`, `outbox_events`
- `reconciliation_runs`, `payment_event_log`
- `CREATE EXTENSION IF NOT EXISTS pgcrypto;` for `gen_random_uuid()` (idempotent — Postgres 13+ ships pgcrypto but it isn't always pre-installed)

## Tests

`mvn test` runs against H2 (`application-test.yml` — Flyway off, `ddl-auto=create-drop`, RabbitMQ listener container disabled, Paymob in stub mode, all `@Scheduled` jobs suppressed). Coverage today is the smoke test only — full integration suites against Testcontainers + a fake Paymob HTTP server are the obvious next step.

## Items deferred per PDF Section 11

- Final list of withdrawal destinations Paymob supports
- Per-method fee model (platform absorbs vs. passes through)
- Multi-currency support (v1 = EGP only)
- Provider failover (Paymob down → Kashier?)
- Tax handling on refunds (coupled with Subscription's deferred VAT decision)
