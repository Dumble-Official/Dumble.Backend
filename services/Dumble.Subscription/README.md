# Dumble.Subscription

Subscription service — platform tiers, bundle subscriptions, escrow, cohort payouts, refunds, gym entry QR codes.

Source of truth for design decisions: `Subscription-Service-Decisions.pdf` at the repo root (22 sections, all locked).

## Stack

- **Spring Boot 3.5.6** (Java 21, Maven)
- **Postgres** (separate database — `dumble_subscription`)
- **Flyway** for schema migrations (`src/main/resources/db/migration/`)
- **Spring Security** + JJWT — local JWT validation against the platform-wide `JWT_SECRET`, matching `Authentication` and `GymService`
- **Spring AMQP / RabbitMQ** — outbox events publish to the `dumble.events` topic exchange

## Running locally

Pre-reqs: Postgres 14+, RabbitMQ, the platform-wide `JWT_SECRET` (base64-encoded HMAC key shared with Authentication / Gateway / Gym).

```
export JWT_SECRET="<same base64 key as Authentication>"
export SUBSCRIPTION_DB_URL="jdbc:postgresql://localhost:5432/dumble_subscription"
export SUBSCRIPTION_DB_USERNAME=postgres
export SUBSCRIPTION_DB_PASSWORD=postgres
export RABBITMQ_HOST=localhost

# create the empty database first
psql -U postgres -c "CREATE DATABASE dumble_subscription;"

mvn spring-boot:run
```

The app starts on port **8182**, with `/api` context path. Flyway runs migrations V1 → V4 on first start.

Health check: `GET http://localhost:8182/api/actuator/health`

## What's implemented

The service is now feature-complete against the locked PDF. ~100 Java files; full V1 + V2 schema migrations; all flows in the design doc are wired end-to-end.

### Endpoints (all under `/api`)

**Participant**
- `GET /me/entitlements` — single source of truth for tier-based feature gating (Decision 3.1)
- `GET /me/plan` — current platform tier
- `POST /me/plan/upgrade` — FREE → PRO with payment (Decision 13.1)
- `POST /me/plan/cancel` — schedule downgrade at period end (Decision 13.2)
- `POST /bundle-subscriptions/checkout` — full checkout flow with idempotency, snapshot, escrow tranching, promo redemption, wallet-or-payment routing
- `GET /me/bundle-subscriptions` — list active + historical
- `POST /me/bundle-subscriptions/{id}/cancel` — cancel auto-renew (Decision 6.1)
- `POST /me/bundle-subscriptions/{id}/entry-token/generate` — fresh QR (Decision 21.2)
- `GET /me/receipts`, `GET /me/receipts/{id}` (Decision 11.5)

**Public**
- `GET /plans` — pricing page (no auth)

**Gym staff**
- `POST /entry-tokens/scan` — scan QR, returns full participant + plan + notes (Decision 21.4)
- `GET /me/gym/{gymId}/entries?period=30d` — entry log
- `GET/POST/DELETE /me/gym/{gymId}/participants/{pid}/notes` — gym staff notes (Decision 21.7)

**Seller dashboard**
- `GET /me/earnings/summary` — Pending / Paid / Lifetime (Decision 5.3 + 15.1)
- `GET /me/earnings/cohorts` — per-cohort breakdown with deferred state surfaced
- `GET /me/earnings/payouts` — historical payouts
- `GET /me/subscribers` — active subscriber list (no PII, Decision 10.2)
- `GET/POST /me/bank-account` — payout destination (Decision 5.4)

**Admin** (requires ROLE_ADMIN)
- `GET /admin/platform/subscriptions` — counts by tier and audience
- `GET /admin/platform/escrow` — Held / Available / PaidOut / Refunded totals
- `GET /admin/platform/refunds` — refund volume

**Webhooks** — system-context, signed JWT required, idempotent on `X-Webhook-Event-Id`
- `POST /webhooks/system/seller-frozen` — enters 7-day Frozen window (Decision 16.2)
- `POST /webhooks/system/seller-unfrozen` — admin clears the freeze
- `POST /webhooks/system/seller-banned` — triggers ban-refund flow (Decision 16.3)

**Self-deactivate / preferences**
- `POST /me/deactivate` — Section 18; blocks if active subs (returns 422 with "contact support")
- `GET/PUT /me/preferences` — Decision 10.1 opt-out toggle from gym subscriber lists

**Admin lifecycle (ROLE_ADMIN)**
- `POST /admin/sellers/{sellerId}/freeze` · `unfreeze` · `ban`
- `POST /admin/sellers/{sellerId}/winding-down` · `winding-down/revert`
- `GET /admin/platform/subscriptions` · `escrow` · `refunds` · `dunning` · `revenue`
- `GET /admin/sellers/top` — leaderboard by revenue

### Schedulers

- `CohortPayoutJob` — weekly Mondays 03:00 UTC; batches HELD entries by seller, fires Paymob payouts, defers if no bank account
- `RenewalJob` — hourly; fires renewal charges for due bundle + platform subs
- `DunningRetryJob` — hourly; re-attempts charges on PAST_DUE subs (Decision 7.3)
- `ExpirationJob` — hourly; marks bundles EXPIRED at endsAt and downgrades cancelled-PRO platform subs to FREE
- `ExpiryNotificationJob` — hourly; emits expiry-warning events at 7d / 1d / day-of (Decision 11.2)
- `FrozenAutoBanJob` — every 30 minutes; auto-bans sellers whose 7-day Frozen window has elapsed; closes drained WindingDown sellers
- `IdempotencyCleanupJob` — nightly purge of expired keys
- `EntryTokenCleanupJob` — every 2 minutes, marks ACTIVE-but-expired tokens as EXPIRED
- `OutboxPublisher` — every 2 seconds, drains `outbox_events` to RabbitMQ

### RabbitMQ

- **Outgoing:** `OutboxPublisher` publishes domain events to `dumble.events` exchange
- **Incoming:** `PaymentEventListener` consumes payout/charge events from Payment via `subscription.inbound` queue

### Inter-service auth (Decision 8.4)

- **Class A** (user-context) — `UserTokenForwarder` reads inbound JWT and attaches it to outbound calls; matches existing `PostServiceClient.cs` pattern
- **Class B** (system-context) — `SystemTokenSigner` mints 60-second JWTs with `iss=subscription`, `aud=<recipient>`, used by all background jobs and HTTP clients when no user context is available

### Audit + outbox

- `OutboxWriter` — writes events transactionally with state changes (Decision 8.5)
- `AuditLogger` — append-only `subscription_event_log` for forensic history (Section 14)

## Building / running

```
export JWT_SECRET="<same base64 key as Authentication>"
export SUBSCRIPTION_DB_URL="jdbc:postgresql://localhost:5432/dumble_subscription"
export SUBSCRIPTION_DB_USERNAME=postgres
export SUBSCRIPTION_DB_PASSWORD=postgres
export RABBITMQ_HOST=localhost
export SERVICE_JWT_SIGNING_KEY="<per-environment service signing key>"
export PAYMENT_SERVICE_URL="http://payment:8183"
export WALLET_SERVICE_URL="http://wallet:8184"
export BUNDLE_MANAGEMENT_URL="http://bundle-management:8081"
export GYM_SERVICE_URL="http://gym-service:8181"

psql -U postgres -c "CREATE DATABASE dumble_subscription;"
mvn spring-boot:run
```

Health: `GET http://localhost:8182/api/actuator/health`

## Items deferred (per PDF Section 22)

These are flagged as open in the design doc and intentionally not blocking v1:

- PRO price validation (10 EGP locked as placeholder)
- Tax/VAT model (deferred until Egypt tax compliance research is done)
- Graceful degradation under outage
- Mass renewal load smoothing
- Receipt PDF rendering (data is captured; bilingual EN+AR layout TBD)
- Soft-delete admin override flows
- Promo code discount-type definition (lives in GymService when built)
- Reconciliation alerting destination (operational runbook item)

## Conventions

- Schema changes go in `src/main/resources/db/migration/V{N}__description.sql` — never modify V1 after it's been applied somewhere.
- All write operations require an `Idempotency-Key` header (24h TTL — Decision 12.3).
- Money is stored in `cents` (BIGINT), not float. Currency is a separate column (CHAR(3), `EGP` for v1).
- Domain events go through the **outbox** — never publish directly to RabbitMQ from a transactional method. Write to `outbox_events` in the same DB tx; the publisher worker handles delivery.
- `subscription_event_log` is append-only — no UPDATE / DELETE. Corrections via compensating entries.
