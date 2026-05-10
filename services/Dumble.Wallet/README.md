# Dumble.Wallet

In-app wallet for refund credits and in-app spending. Same wallet for participants and sellers; bundle earnings do **not** pass through here — those live in Subscription's escrow and pay out directly to the seller's bank via Payment.

Source of truth for design decisions: `Wallet-Service-Decisions.pdf` at the repo root (8 sections, all locked).

## Stack

- **Spring Boot 3.5.6** (Java 21, Maven)
- **Postgres** (separate database — `dumble_wallet`)
- **Flyway** for schema migrations (`src/main/resources/db/migration/`)
- **Spring Security** + JJWT — local JWT validation against the platform-wide `JWT_SECRET`
- **Spring AMQP / RabbitMQ** — outbox events publish to the `dumble.events` topic exchange; consumes from `wallet.inbound`

## Running locally

Pre-reqs: Postgres 14+, RabbitMQ, the platform-wide `JWT_SECRET` and a service-JWT signing key.

```
export JWT_SECRET="<same base64 key as Authentication>"
export SERVICE_JWT_SIGNING_KEY="<>= 32-byte base64-encoded key shared across services>"
export WALLET_DB_URL="jdbc:postgresql://localhost:5432/dumble_wallet"
export WALLET_DB_USERNAME=postgres
export WALLET_DB_PASSWORD=postgres
export RABBITMQ_HOST=localhost
export PAYMENT_SERVICE_URL=http://localhost:8183

psql -U postgres -c "CREATE DATABASE dumble_wallet;"

mvn spring-boot:run
```

App starts on port **8184** with context-path `/api`. Flyway runs the V1 migration on first boot.

Health check: `GET http://localhost:8184/api/actuator/health`

## What's implemented

Service is feature-complete against the locked PDF.

### Endpoints (all under `/api`)

**System / inter-service** (signed system JWT, Decision 6.4 Class B)
- `POST /wallet/credit` — refund / chargeback / admin-adjustment credit (Decision 3.1)
- `POST /wallet/debit` — in-app spend at checkout (Decision 4.1) — actually accepts user JWT too; Subscription forwards the user's bearer
- `GET /wallet/{userId}/summary` — Subscription's pre-checkout balance check

**User** (user JWT)
- `GET /wallet/me/summary` — Available + Pending + recent activity (Decision 7.1)
- `GET /wallet/me/entries?page&size` — paginated full ledger
- `POST /wallet/me/withdrawals` — request a manual withdrawal (Decision 4.3)
- `POST /wallet/me/withdrawals/{id}/cancel` — cancel a still-PENDING withdrawal (Decision 4.3)
- `GET /wallet/me/withdrawals` — withdrawal history

**Admin** (`ROLE_ADMIN`)
- `GET /admin/wallet/{userId}` — read any user's wallet (audited)
- `POST /admin/wallet/{userId}/adjust` — manual CREDIT or DEBIT with mandatory memo (Decisions 5.1, 5.3)

### Money flow

Ledger is append-only (Decision 5.1) — `wallet_entries` rows are never updated or deleted; corrections happen via `ADMIN_ADJUSTMENT` compensating entries. Database trigger blocks UPDATE/DELETE.

Cached `Wallet.availableCents / pendingCents` are atomically updated alongside every ledger insert (Decision 2.3). A daily reconciliation job (Decision 5.2, default 02:30 UTC) sums the ledger and alerts on cache divergence.

### Withdrawal lifecycle

```
PENDING ──▶ SUBMITTING ──▶ SENT ──▶ COMPLETED       (happy path)
   │                       │         ▲
   │                       └─────────┴──▶ FAILED  ──▶ wallet credited back
   ▼
CANCELLED  (user-initiated, allowed only while PENDING)
```

`SUBMITTING` is an internal sub-state of `PENDING` that closes the cancel/Phase-2 race — see Decision 4.3. Once we hand off to Payment we can't safely reverse without coordinating with Paymob, so the transition `PENDING → SUBMITTING` happens in its own short tx (under a row-level pessimistic lock) before the HTTP call. Cancel is only accepted on `PENDING`; if it slips in between Phase 1 and the lock acquisition the row is already `CANCELLED` and we abort without dispatching to Payment.

Wallet does not talk to Paymob (Decision 1.3). It hands off to Payment service via `POST /api/payment/withdrawals`; Payment emits `WithdrawalCompleted` / `WithdrawalFailed` events on the `wallet.inbound` queue and Wallet finalises the ledger.

### Events

**Published** (Decision 6.3) — outbox-pattern (Decision 6.5):
- `WalletCredited`, `WalletDebited`
- `WithdrawalRequested`, `WithdrawalCompleted`, `WithdrawalFailed`

**Consumed** (Decision 6.2):
- `payment.withdrawal.completed`
- `payment.withdrawal.failed`

### Concurrency + idempotency

- Idempotency-Key required on every monetary write (Decisions 3.1, 4.1, 4.3); 24h TTL.
  Insert-first-with-PENDING gate via `IdempotencyKeyStore` so concurrent peers serialize on the PK.
- Pessimistic lock on the wallet row inside debit / withdrawal-create (`findByIdForUpdate`) prevents race-induced over-spend.
- Optimistic `@Version` on Wallet + WithdrawalRequest as a second-line defense.
- AMQP listener path dedupes via `inbound_listener_events` (PK on event id).

### Withdrawal minimum

Configurable per environment via `wallet.withdrawal.minimum-cents` (default 5000 = 50 EGP per Decision 4.5). Below the minimum, the API rejects with 422.

## Schema

Single migration so far (`V1__initial_schema.sql`) covers:
- `wallets`, `wallet_entries` (append-only via DB trigger), `withdrawal_requests`
- `idempotency_keys`, `outbox_events`, `inbound_listener_events`, `wallet_event_log`
- `CREATE EXTENSION IF NOT EXISTS pgcrypto;` for `gen_random_uuid()` (idempotent — Postgres 13+ ships pgcrypto but it isn't always pre-installed)

## Tests

`mvn test` runs against H2 (test profile uses `application-test.yml` — Flyway off, `ddl-auto=create-drop`, RabbitMQ listener container disabled). Coverage today:

- `WalletServiceTest` — credit auto-creates a wallet, debit fails closed on insufficient balance, summary computes available + recent activity, parseSource accepts both PascalCase (`BanRefund` — what Subscription's existing client sends) and SNAKE_CASE.
- `DumbleWalletApplicationTests` — Spring context loads cleanly.

**Known gap**: the append-only enforcement on `wallet_entries` is a Postgres `plpgsql` trigger and isn't installed when H2 generates the schema from JPA. Tests that need to verify "DB rejects UPDATE/DELETE on a ledger row" should switch to Testcontainers + real Postgres so the V1 migration runs verbatim. Service-layer correctness is otherwise covered by H2.
