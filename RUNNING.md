# Running Dumble.Backend locally

Everything runs in Docker. The Flutter app talks to **one** address — the API
gateway on **`http://localhost:8080`** — and the gateway routes to every
backend service internally. You do not start services individually or hit them
on their own ports.

---

## 1. Prerequisites

- **Docker Desktop** running (Compose v2 — `docker compose`, not `docker-compose`).
- ~8 GB free RAM for Docker and ~15 GB disk (the first build pulls Java, .NET,
  Postgres/MySQL/SQL Server/Mongo images and builds the Python chatbot).
- Nothing else listening on **port 8080** (see Troubleshooting if it's taken).

---

## 2. Create the env file (one time)

All secrets live in `release/.env`, which is **gitignored** — it is not in the
repo. You have two options:

**Option A — get the ready-made file from the backend lead (fastest).**
Ask for the `release/.env` file and drop it at `release/.env`. It already has
working dev values plus the Gemini keys and Google client ID.

**Option B — build it yourself from the template.**
```bash
cp release/.env.example release/.env
```
Then open `release/.env` and fill in every value. The ones that MUST be set or
compose refuses to start:

| Variable | What it is |
| --- | --- |
| `JWT_SECRET` | Base64 HS256 key for user tokens (`openssl rand -base64 48`) |
| `SERVICE_JWT_SIGNING_KEY` | Base64 HS256 key for service-to-service tokens |
| `AUTH_DB_PASSWORD` … `SUBSCRIPTION_DB_PASSWORD` | One password per database |
| `BUNDLE_DB_PASSWORD` | SQL Server rule: 8+ chars, upper/lower/digit/symbol |
| `MONGO_PASSWORD` | Mongo root password |
| `GOOGLE_CLIENT_ID` | Google OAuth web client ID (sign-in-with-google) |
| `ADMIN_PASSWORD` | First-boot admin user password |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins, e.g. `http://localhost:3000` |
| `GEMINI_API_KEYS` | Comma-separated Gemini keys for the AI coach |
| `INTERNAL_API_SECRET` | Shared secret between gateway and the AI coach |

> The app still boots if `GOOGLE_CLIENT_ID`, `CLOUDINARY_URL`, or
> `GEMINI_API_KEYS` are placeholders — only those specific features
> (Google login, image upload, AI coach) fail until real values are in.

---

## 3. Start everything (one command)

From the repo root:

```bash
docker compose -f release/docker-compose.yml up -d --build
```

Compose starts services in dependency order automatically: databases and
message brokers first, then the backend services once their databases report
healthy, then the gateway last. You do not need to start anything by hand or in
a particular sequence — `depends_on` + healthchecks handle it.

**The first run takes 5–15 minutes** because it builds every image (the Python
`fitcoach` image alone is ~5 min). Later runs start in seconds.

---

## 4. Wait until it's ready

```bash
# Gateway up?  → {"status":"UP"}
curl http://localhost:8080/actuator/health

# See what's running and whether DBs are healthy
docker compose -f release/docker-compose.yml ps
```

Give it ~60–90 seconds after the command returns — the Java services
(auth, gateway, gym, payment, wallet, subscription) take time to boot even
after their containers show "Up". When `/actuator/health` returns
`{"status":"UP"}` you're good.

---

## 5. Point the Flutter app at the gateway

The base URL depends on where the app runs:

| Where the app runs | Base URL |
| --- | --- |
| iOS simulator | `http://localhost:8080` |
| Android emulator | `http://10.0.2.2:8080` |
| Physical phone (same Wi-Fi) | `http://<your-computer-LAN-IP>:8080` |
| Flutter web / desktop | `http://localhost:8080` |

> **Android emulator does NOT see `localhost`** — that resolves to the emulator
> itself. Use `10.0.2.2`, which the emulator maps to your host machine.
> For a physical device, find your LAN IP (`ipconfig` on Windows /
> `ifconfig` or `ip addr` on Mac/Linux) and make sure the phone is on the same
> network.

### API surface (everything is under the gateway)

| Path prefix | Service |
| --- | --- |
| `/api/auth/**`, `/api/users/**` | Authentication / users |
| `/api/posts/**`, `/api/comments/**`, `/api/hashtags/**` | Posts |
| `/api/social/**`, `/api/feed/**` | Social / feed |
| `/api/chat/**`, `/hubs/chat/**` | Messaging chat (real-time, SignalR) |
| `/api/notifications/**`, `/hubs/notifications/**` | Notifications |
| `/api/bundles/**`, `/api/categories/**` | Bundles |
| `/api/gyms/**`, `/api/amenities/**` | Gyms |
| `/api/wallet/**` | Wallet |
| `/api/payment/**` | Payments |
| `/api/me/**`, `/api/plans` | Subscription / entitlements |
| `/api/coach/**` | AI fitness coach (PRO plan only) |

> `/api/chat` is the **messaging** service (user-to-user chat).
> `/api/coach` is the **AI chatbot**. They are different services — don't mix them up.
>
> The AI coach is gated: a user on the FREE plan gets `403` on `/api/coach/**`.
> They must be on the PRO plan (via `/api/me/plan/upgrade`).

---

## 6. Stopping and restarting

```bash
# Stop everything (keeps data in volumes)
docker compose -f release/docker-compose.yml stop

# Start again (no rebuild)
docker compose -f release/docker-compose.yml up -d

# Full teardown INCLUDING databases (wipes all data — fresh start)
docker compose -f release/docker-compose.yml down -v
```

---

## Troubleshooting

**`bind: address already in use` / port 8080 taken.**
Something else on your machine owns 8080. Either stop it, or remap the gateway
to another host port without editing the committed file — create
`release/docker-compose.override.yml`:
```yaml
services:
  gateway:
    ports: !override
      - "18080:8080"
```
Then the gateway is on `http://localhost:18080` and the Flutter base URL
changes to match. (Compose auto-loads `docker-compose.override.yml`; it's
gitignored so it stays local to you.)

**A service shows "Up" but the app gets connection errors / 503.**
Java services need ~30–60s after the container starts before they accept
traffic. Wait and recheck `curl http://localhost:8080/actuator/health`.

**`... must be set in release/.env` on startup.**
A required variable is missing or empty in `release/.env`. Re-check the table
in step 2 against your file.

**See why a specific service failed:**
```bash
docker compose -f release/docker-compose.yml logs gateway --tail 50
docker compose -f release/docker-compose.yml logs authentication --tail 50
# replace with any service name from `... ps`
```

**`401 Unauthorized` on every call.**
The request needs a valid JWT. Register/login via `/api/auth/register` and
`/api/auth/login` first, then send `Authorization: Bearer <token>` on
subsequent calls. The only open endpoints are register, login, refresh,
google, and the health checks.

**`403` specifically on `/api/coach/**`.**
That user is on the FREE plan. The AI coach is a PRO-tier feature.

**First build is very slow / seems stuck.**
The Python `fitcoach` image downloads PyTorch + sentence-transformers and a
MediaPipe model at build time (~5 min on a good connection). Watch progress:
```bash
docker compose -f release/docker-compose.yml logs fitcoach --tail 20
```

**Reset to a clean slate (forget all data and rebuild):**
```bash
docker compose -f release/docker-compose.yml down -v
docker compose -f release/docker-compose.yml up -d --build
```

---

## Notes for the backend side (not needed to just run the app)

- The dev `release/docker-compose.override.yml` on the backend machines remaps
  the gateway to `18080` and exposes individual services on `18xxx` host ports
  for QA tests. The Flutter team does **not** need it — the committed
  `docker-compose.yml` alone gives you the gateway on `8080`.
- `release/docker-compose.test.fitcoach.yml` is test-only (exposes the chatbot
  directly on `18000`); not needed to run the app.
