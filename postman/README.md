# Dumble Backend — Postman Collection

A comprehensive, ready-to-run Postman collection for the entire Dumble API (all 13 services behind the gateway).

## Files

| File | Purpose |
|------|---------|
| `Dumble-Backend.postman_collection.json` | The collection (Postman **Collection v2.1**) — ~150 endpoints in 19 resource folders + ordered end-to-end workflows. |
| `Dumble-Local.postman_environment.json` | Environment pointing at `http://localhost:8080` (the gateway when running `release/docker-compose.yml`). |
| `Dumble-Production.postman_environment.json` | Environment pointing at the deployed gateway `http://167.172.180.189:8080`. |

## Setup (≈30 seconds)

1. **Import** all four JSON files into Postman (`Import` → drag the files in).
2. **Select an environment** (top-right): *Dumble Local* or *Dumble Production*.
3. Open the environment and set **`admin_password`** (and optionally point `base_url` elsewhere).
4. Run any **Login** request (e.g. `01 · Auth → Login`, or the first step of any workflow). Its test script stores the JWT in the `auth_token` collection variable.
5. That's it — **every other request inherits collection-level Bearer auth** (`Authorization: Bearer {{auth_token}}`), so you can fire any endpoint immediately.

> New here? Run `00 · WORKFLOWS → A · Auth & Profile → 1. Register a new user` instead of logging in — it creates a fresh account and captures the token for you.

## How it's organized

- **`00 · WORKFLOWS`** — ordered, end-to-end scenarios. Run a sub-folder top-to-bottom (or use the Collection Runner). Scripts chain requests by saving ids/tokens to variables:
  - **A · Auth & Profile** — register → get me → onboarding → update.
  - **B · Post lifecycle** — login → create post → read → react → comment → list → update → delete.
  - **C · Social graph & feed** — login → follow → track behavior → home feed → suggested users → unfollow.
  - **D · Chat** — login → create conversation → send message → list → mark read.
- **`01`–`19`** — one folder per resource/feature area (Auth, Users, Posts, Comments, Reactions & Hashtags, Social, Feed & Recommendations, Chat, Notifications, Gyms, Amenities, Gym Registrations, Bundles & Categories, Plans/Subscriptions/Seller, Payment, Wallet, Schedule, FitCoach, Admin).

## What each request includes

- Correct **HTTP method**, full **path**, and **path/query parameters** with example values.
- A realistic **example request body** (JSON; multipart/form-data where the API expects file uploads — e.g. create-post, create-bundle, gym images).
- **Auth notes** in the description: most endpoints use the collection Bearer token; public ones (register/login/google, public GETs, webhooks, FitCoach health) are marked `noauth`. Role-gated endpoints (ADMIN, MODERATOR, SELLER, GYM_OWNER, or the Payment `SERVICE` system-JWT) are labelled in the name/description.
- A **test script** asserting the status code (`pm.test`), plus key-field checks and **variable capture** on creates/reads (e.g. `post_id`, `comment_id`, `conversation_id`, `gym_id`, `bundle_id`, `withdrawal_id`).
- **Example responses** on the key endpoints (login, register, create post, home feed, …).

## Collection variables (auto-managed)

`base_url`, `admin_password`, `auth_token`, `refresh_token`, `user_id`, `target_user_id`, `post_id`, `comment_id`, `conversation_id`, `message_id`, `gym_id`, `registration_id`, `bundle_id`, `category_id`, `bundle_subscription_id`, `withdrawal_id`, `charge_id`, `refund_id`, `payment_method_id`, `schedule_item_id`, `role_request_id`, …

Most are populated automatically by the workflow/login/create scripts. A few (e.g. `target_user_id` for follow/chat) you set manually to point at a second account.

## Notes & gotchas

- **Idempotency-Key**: payment/wallet/subscription mutations include an `Idempotency-Key: {{$guid}}` header (a fresh GUID each send) — required by those endpoints.
- **Payment `tokenize`/charges/refunds** require a **system JWT** (`aud=payment`), not a normal user token; they're included for completeness but aren't reachable with a plain login.
- **SignalR hubs** (`/hubs/chat`, `/hubs/notifications`) are websockets, not REST, so they're not in the collection; use `POST /api/auth/hub-token` to get a negotiation token.
- **FitCoach** streaming endpoints return Server-Sent Events; Postman shows the raw stream.
- Base URL is the **gateway** (`:8080`); individual services aren't called directly.

## Regenerating

The collection is produced by a generator script (`gen_postman.py`) from a structured endpoint definition, so it stays consistent. Re-run it after API changes to refresh the JSON.
