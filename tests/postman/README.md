# Dumble.Backend — Postman / Newman test harness

This directory is the plumbing only. **Claude generates collections on demand at test time**
from the current state of each service's controllers + business rules, then throws them away.
No collection file is committed to git, so nothing rots when you edit a controller.

**Docker is treated as always-on**, like a real QA environment. If the stack is
already running, Claude tests against it. If it's not, Claude brings it up once
with `docker compose up -d` and leaves it running for the next time. The stack
is never torn down by the harness.

## Workflow

You say *"test the auth service"* (or any service / set / "everything").
Claude does the whole loop end to end:

1. **Pre-flight** — probe `http://localhost:8080/actuator/health`. If healthy, continue. If not, `docker compose -f release\docker-compose.yml --env-file release\.env up -d` and wait for health (up to 180s). Stack is left running afterwards.
2. **Design the scenarios** — re-read the target service's controllers, services, and entities right now, then plan a real-scenario test plan covering:
   - The golden path a real user would walk (register → login → use → logout, etc).
   - Validation edge cases (bad email, weak password, oversized upload).
   - Authorization corner cases (wrong role, missing token, banned user, expired token, revoked refresh).
   - Business-rule violations (duplicate email, second gym owner, removing the owner, race conditions documented in the design docs).
3. **Emit a fresh `collections/<service>.postman_collection.json`** for this run only. Gitignored.
4. **Run Newman.**
5. **Parse the JSON reports** and produce a per-service summary table + the first failing assertion per service + paths to the HTML reports.
6. Leave the collection on disk for inspection (gitignored, overwritten next run).

## What you say → what I do

| You say | I do |
| --- | --- |
| *"test auth"* | design scenarios + corner cases, generate, run, report |
| *"test gym and subscription"* | same, for those two services |
| *"test everything"* | same, for all 10 service collections |
| *"test auth, only the happy path"* | scoped: no error / edge-case scenarios |
| *"re-run auth"* | re-use the last generated collection if it exists, run, report |
| *"test auth, stack is up"* | skip the auto-up probe and just test (faster) |

The stack stays up after testing — `docker compose down` is your call, not mine.

## What's wired up

| Piece | Where | Purpose |
| --- | --- | --- |
| Newman CLI | global npm | `newman --version` → 6.x. Runs Postman collections from the shell. |
| HTML reporter | global npm | `newman-reporter-htmlextra` — pretty per-collection HTML reports. |
| Environment template | `environments/local.postman_environment.json` | Gateway URL + test-user vars + empty token placeholders. Safe to commit. |
| Runner script | `run.ps1` | Pre-flight gateway probe, runs Newman per collection, summarises pass/fail. Does NOT touch docker. |
| .gitignore | `.gitignore` | Keeps `reports/`, generated `collections/*.postman_collection.json`, runtime env files out of git. |
| Collections folder | `collections/` | Empty by design. Filled at test time, never committed. |
| Reports folder | `reports/` | Newman writes per-collection JSON + HTML reports here. Gitignored. |

## How scenarios are designed

When you ask Claude to test a service, it doesn't just translate every endpoint
into a smoke request. It reads the service's *behavior*:

- **Authentication** — refresh-token rotation must be single-use; ban must take effect before the access-token TTL; register must not leak duplicate-email; etc.
- **Gym** — only `GYM_OWNER` can create a gym; only one staff with role `GYM` per gym; deleting the GYM staff is forbidden; ownership check on update vs delete differs.
- **Subscription** — escrow only leaves `Held` via cohort payout or ban refund; double-charge is blocked by idempotency key; canceled auto-renew preserves access through current period.
- ...etc per service, sourced from the locked decisions docs in the repo root.

Each scenario becomes one Postman folder inside the collection, with multiple
ordered requests + `pm.test(...)` assertions on status, response shape, and side
effects (token populated, follow-up request rejected, etc).

## Manual invocation (if you ever want to bypass Claude)

```powershell
cd Dumble.Backend\tests\postman
.\run.ps1                  # runs every collection in collections/
.\run.ps1 -Only auth       # only the auth collection
.\run.ps1 -Only auth,gym   # only those two
.\run.ps1 -WaitSeconds 60  # wait longer for the gateway pre-flight probe
```

The script exits 0 on all-pass, non-zero on any failure, so it's CI-friendly.
You'd need to either ask Claude to generate the collections first, or author
them yourself (Postman desktop → Export → drop into `collections/`).

## Secrets posture

- `environments/local.postman_environment.json` is committed. No real secrets — only placeholder test-user emails / passwords that the generated collections seed into the Auth DB at run time.
- Newman never reads `release/.env`. Service-level secrets live with the running stack only.
- Any per-developer override goes into `environments/*.local.postman_environment.json` (gitignored).
