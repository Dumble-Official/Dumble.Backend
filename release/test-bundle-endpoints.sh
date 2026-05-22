#!/usr/bin/env bash
#
# end-to-end test script — Dumble Bundle Management Service
#
# Prerequisites:
#   1. Docker Desktop running
#   2. Node.js (for the JWT generation helper)
#   3. curl, jq
#
# Usage:
#   cd release
#   chmod +x test-bundle-endpoints.sh
#   ./test-bundle-endpoints.sh
#
# What is tested:
#   • Health endpoints (/health/live, /health/ready)
#   • Category CRUD  (Create, Get All, Update, Delete) — protected by ADMIN role
#   • Bundle CRUD    (Create, Get By Id, Update, Delete) — protected by roles
#   • Authorization enforcement (anonymous requests rejected, wrong roles rejected)
#   • Validation (empty names, invalid statuses, bad IDs, etc.)

set -euo pipefail

# ─── Colours ────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
PASS=0 FAIL=0

pass() { PASS=$((PASS+1)); echo -e "  ${GREEN}✓ PASS${NC}  $1"; }
fail() { FAIL=$((FAIL+1)); echo -e "  ${RED}✗ FAIL${NC}  $1"; }
info() { echo -e "  ${CYAN}ℹ${NC}  $1"; }
warn() { echo -e "  ${YELLOW}⚠${NC}  $1"; }

# ─── Configuration ──────────────────────────────────────────────────────
BASE_URL="http://localhost:5012"
TEST_DIR="$(cd "$(dirname "$0")/.." && pwd)"                       # project root

# JWT secret must match what the service expects (the placeholder from appsettings).
JWT_SECRET_B64="dGhpcyBpcyBhIHZlcnkgc2VjdXJlIHNlY3JldCBrZXkgZm9yIGp3dCB0b2tlbiBnZW5lcmF0aW9u"  # base64 of "this is a very secure secret key for jwt token generation" — padded
# Actually, the appsettings.json has: "this is a very secure secret key for jwt token generation"
# which is 61 chars. Base64 of that is: dGhpcyBpcyBhIHZlcnkgc2VjdXJlIHNlY3JldCBrZXkgZm9yIGp3dCB0b2tlbiBnZW5lcmF0aW9u
# But that's not exactly 64 bytes. Let's use a proper 64-byte key in base64.
# For testing, we'll read it from .env or use a fixed one.

# Helper to generate JWTs for different roles.
# Uses Python (which is typically available) to create a HS256-signed JWT.
generate_jwt() {
    local user_id="$1"
    local user_type="$2"
    local roles="$3"   # JSON array string, e.g. '["ROLE_ADMIN"]'
    local display_name="${4:-Test User}"

    python3 -c "
import json, time, base64, hmac, hashlib

# Read JWT secret from the environment or use the one from appsettings.json
secret = '${JWT_SECRET:-this is a very secure secret key for jwt token generation}'

header = json.dumps({'alg': 'HS256', 'typ': 'JWT'}).encode()
payload_dict = {
    'sub': '${user_id}',
    'userId': '${user_id}',
    'userType': '${user_type}',
    'displayName': '${display_name}',
    'roles': ${roles},
    'iat': int(time.time()),
    'exp': int(time.time()) + 3600
}
payload = json.dumps(payload_dict).encode()

def b64encode(data):
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode()

header_b64 = b64encode(header)
payload_b64 = b64encode(payload)

signature_input = f'{header_b64}.{payload_b64}'.encode()
signature = hmac.new(secret.encode(), signature_input, hashlib.sha256).digest()
sig_b64 = b64encode(signature)

print(f'{header_b64}.{payload_b64}.{sig_b64}')
"
}

# ─── Setup: start infra ────────────────────────────────────────────────
cleanup() {
    local exit_code=$?
    info "Cleaning up..."
    cd "$(dirname "$0")"
    docker compose -f docker-compose.test.bundle.yml down --remove-orphans 2>/dev/null || true
    # Kill backgrounded bundle service
    if [ -n "${BUNDLE_PID:-}" ]; then
        kill "$BUNDLE_PID" 2>/dev/null || true
    fi
    exit $exit_code
}
trap cleanup EXIT INT TERM

echo -e "\n${CYAN}══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  Bundle Management Service — End-to-End Tests${NC}"
echo -e "${CYAN}══════════════════════════════════════════════════════════${NC}\n"

# 1. Start infrastructure (SQL Server + Redis) via test compose
echo -e "${YELLOW}[1/4] Starting test infrastructure (SQL Server + Redis)...${NC}"
cd "$(dirname "$0")"
docker compose -f docker-compose.test.bundle.yml up -d

echo "Waiting for SQL Server to be healthy..."
for i in $(seq 1 30); do
    if docker compose -f docker-compose.test.bundle.yml exec bundle-db \
        /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "TestPassword123!" -C -Q "SELECT 1" &>/dev/null; then
        info "SQL Server is ready."
        break
    fi
    if [ "$i" -eq 30 ]; then
        fail "SQL Server did not become healthy within timeout"
        exit 1
    fi
    sleep 3
done

echo "Waiting for Redis to be healthy..."
for i in $(seq 1 15); do
    if docker compose -f docker-compose.test.bundle.yml exec redis redis-cli ping &>/dev/null; then
        info "Redis is ready."
        break
    fi
    if [ "$i" -eq 15 ]; then
        fail "Redis did not become healthy within timeout"
        exit 1
    fi
    sleep 2
done

# 2. Build and run the bundle service locally (pointing at the Docker infra)
echo -e "\n${YELLOW}[2/4] Building & starting Bundle Management Service...${NC}"
cd "$TEST_DIR/services/Dumble.BundleManagementService"

# Create a temporary appsettings for testing
cat > appsettings.Testing.json << 'EOF'
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning"
    }
  },
  "ConnectionStrings": {
    "DatabaseConnection": "Server=localhost,1433;Database=dumble_bundles;User Id=sa;Password=TestPassword123!;TrustServerCertificate=True"
  },
  "Cloudinary": {
    "CloudName": "test",
    "ApiKey": "test",
    "ApiSecret": "test"
  },
  "AllowedHosts": "*",
  "Jwt": {
    "Secret": "this is a very secure secret key for jwt token generation",
    "Issuer": "dumble-auth",
    "Audience": "dumble-app"
  },
  "Urls": "http://localhost:5012"
}
EOF

# Build the project
dotnet build Dumble.BundleManagementService.API/Dumble.BundleManagementService.API.csproj -c Release --no-restore 2>&1 | tail -5

# Start the service in the background
ASPNETCORE_ENVIRONMENT="Testing" \
ASPNETCORE_URLS="http://localhost:5012" \
    dotnet run --project Dumble.BundleManagementService.API/Dumble.BundleManagementService.API.csproj &
BUNDLE_PID=$!

# Wait for the service to start
echo "Waiting for bundle service to start..."
for i in $(seq 1 30); do
    if curl -sf "$BASE_URL/health/live" > /dev/null 2>&1; then
        info "Bundle service is running."
        break
    fi
    if [ "$i" -eq 30 ]; then
        fail "Bundle service did not start within timeout"
        exit 1
    fi
    sleep 2
done

# ─── Generate test JWTs ─────────────────────────────────────────────────
echo -e "\n${YELLOW}[3/4] Generating test JWT tokens...${NC}"

ADMIN_TOKEN=$(generate_jwt "admin-001" "Admin" '["ROLE_ADMIN"]' "Admin User")
GYM_OWNER_TOKEN=$(generate_jwt "gymowner-001" "GymOwner" '["ROLE_GYM_OWNER"]' "Gym Owner")
GYM_TOKEN=$(generate_jwt "gym-001" "Gym" '["ROLE_GYM"]' "Gym Account")
TRAINER_TOKEN=$(generate_jwt "trainer-001" "Trainer" '["ROLE_TRAINER"]' "Trainer User")
PARTICIPANT_TOKEN=$(generate_jwt "participant-001" "Participant" '[]' "Regular User")

ADMIN_AUTH="Authorization: Bearer $ADMIN_TOKEN"
GYM_OWNER_AUTH="Authorization: Bearer $GYM_OWNER_TOKEN"
GYM_AUTH="Authorization: Bearer $GYM_TOKEN"
TRAINER_AUTH="Authorization: Bearer $TRAINER_TOKEN"
PARTICIPANT_AUTH="Authorization: Bearer $PARTICIPANT_TOKEN"

# ─── Helper functions ───────────────────────────────────────────────────

assert_status() {
    local method="$1" path="$2" expected="$3" label="$4"
    shift 4
    local response_code
    response_code=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$BASE_URL$path" "$@")
    if [ "$response_code" = "$expected" ]; then
        pass "$label (HTTP $response_code)"
    else
        fail "$label — expected HTTP $expected, got $response_code"
    fi
}

assert_body_contains() {
    local method="$1" path="$2" substring="$3" label="$4"
    shift 4
    local body
    body=$(curl -sf -X "$method" "$BASE_URL$path" "$@")
    if echo "$body" | grep -q "$substring"; then
        pass "$label (body contains \"$substring\")"
    else
        fail "$label — expected body to contain \"$substring\", got: $(echo "$body" | head -c 200)"
    fi
}

capture_body() {
    local method="$1" path="$2" label="$3"
    shift 3
    curl -sf -X "$method" "$BASE_URL$path" "$@"
}

# ─── BEGIN TESTS ─────────────────────────────────────────────────────────
echo -e "\n${YELLOW}[4/4] Running endpoint tests...${NC}\n"

echo -e "${CYAN}── Health Checks ──────────────────────────────────────────${NC}"

assert_status GET "/health/live" 200 "GET /health/live returns 200"
assert_status GET "/health/ready" 200 "GET /health/ready returns 200"

echo -e "\n${CYAN}── Category Endpoints ─────────────────────────────────────${NC}"

# 1. Create a category (requires ADMIN)
assert_status POST "/api/categories" 201 "POST /api/categories (ADMIN, valid)" \
    -H "$ADMIN_AUTH" \
    -H "Content-Type: application/json" \
    -d '{"name":"Strength Training"}'

# Create another for later use
assert_status POST "/api/categories" 201 "POST /api/categories — second (ADMIN)" \
    -H "$ADMIN_AUTH" \
    -H "Content-Type: application/json" \
    -d '{"name":"Cardio"}'

# 2. Create category — empty name (should be rejected by FluentValidation → 400)
assert_status POST "/api/categories" 400 "POST /api/categories (ADMIN, empty name → 400)" \
    -H "$ADMIN_AUTH" \
    -H "Content-Type: application/json" \
    -d '{"name":""}'

# 3. Create category — anonymous (no auth → 401)
assert_status POST "/api/categories" 401 "POST /api/categories (anonymous → 401)" \
    -H "Content-Type: application/json" \
    -d '{"name":"Should Fail"}'

# 4. Create category — participant role (→ 403 Forbidden)
assert_status POST "/api/categories" 403 "POST /api/categories (participant → 403)" \
    -H "$PARTICIPANT_AUTH" \
    -H "Content-Type: application/json" \
    -d '{"name":"Should Fail"}'

# 5. Get all categories (public)
echo -n "  Fetching categories to extract IDs... "
ALL_CATEGORIES=$(assert_status GET "/api/categories" 200 "GET /api/categories (anonymous)")
CATEGORIES_BODY=$(curl -sf "$BASE_URL/api/categories")
FIRST_CATEGORY_ID=$(echo "$CATEGORIES_BODY" | jq -r '.[0].Id // empty')
SECOND_CATEGORY_ID=$(echo "$CATEGORIES_BODY" | jq -r '.[1].Id // empty')
echo "done. IDs: $FIRST_CATEGORY_ID, $SECOND_CATEGORY_ID"

if [ -n "$FIRST_CATEGORY_ID" ]; then
    pass "GET /api/categories returned at least one category (ID=$FIRST_CATEGORY_ID)"
else
    fail "GET /api/categories did not return any categories"
fi

# 6. Update category (requires ADMIN)
if [ -n "$FIRST_CATEGORY_ID" ]; then
    assert_status PUT "/api/categories/$FIRST_CATEGORY_ID" 200 "PUT /api/categories/{id} (ADMIN, valid)" \
        -H "$ADMIN_AUTH" \
        -H "Content-Type: application/json" \
        -d '{"name":"Strength & Conditioning"}'
fi

# 7. Update category — anonymous (→ 401)
if [ -n "$FIRST_CATEGORY_ID" ]; then
    assert_status PUT "/api/categories/$FIRST_CATEGORY_ID" 401 "PUT /api/categories/{id} (anonymous → 401)" \
        -H "Content-Type: application/json" \
        -d '{"name":"Should Fail"}'
fi

# 8. Update category — participant (→ 403)
if [ -n "$FIRST_CATEGORY_ID" ]; then
    assert_status PUT "/api/categories/$FIRST_CATEGORY_ID" 403 "PUT /api/categories/{id} (participant → 403)" \
        -H "$PARTICIPANT_AUTH" \
        -H "Content-Type: application/json" \
        -d '{"name":"Should Fail"}'
fi

# 9. Update category — non-existent ID (→ 404)
assert_status PUT "/api/categories/00000000-0000-0000-0000-000000000000" 404 "PUT /api/categories/nonexistent → 404" \
    -H "$ADMIN_AUTH" \
    -H "Content-Type: application/json" \
    -d '{"name":"Nope"}'

# 10. Delete category (requires ADMIN)
if [ -n "$SECOND_CATEGORY_ID" ]; then
    assert_status DELETE "/api/categories/$SECOND_CATEGORY_ID" 204 "DELETE /api/categories/{id} (ADMIN → 204)" \
        -H "$ADMIN_AUTH"
fi

# 11. Delete category — anonymous (→ 401)
if [ -n "$SECOND_CATEGORY_ID" ]; then
    assert_status DELETE "/api/categories/$SECOND_CATEGORY_ID" 401 "DELETE /api/categories/{id} (anonymous → 401)"
fi

# 12. Delete category — non-existent ID (→ 404)
assert_status DELETE "/api/categories/00000000-0000-0000-0000-000000000000" 404 "DELETE /api/categories/nonexistent → 404" \
    -H "$ADMIN_AUTH"

echo -e "\n${CYAN}── Bundle Endpoints ────────────────────────────────────────${NC}"

# 13. Create a bundle — needs GYM_OWNER, GYM, or TRAINER
# First, create a dummy image file for testing
BUNDLE_IMAGES_DIR=$(mktemp -d)
echo "fake image content" > "$BUNDLE_IMAGES_DIR/test.jpg"

CREATE_BUNDLE_PAYLOAD="-F Name='Test Bundle' -F Description='A test bundle description' -F Price=49.99 -F Status=Draft -F ExpiresOn=$(date -u +%Y-%m-%dT%H:%M:%SZ -d '+30 days') -F CategoryId=$FIRST_CATEGORY_ID"

assert_status POST "/api/bundles" 201 "POST /api/bundles (GYM_OWNER, valid → 201)" \
    -H "$GYM_OWNER_AUTH" \
    -F "Name=Test Bundle" \
    -F "Description=A test bundle description" \
    -F "Price=49.99" \
    -F "Status=Draft" \
    -F "ExpiresOn=$(date -u -d '+30 days' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo '2099-12-31T23:59:59Z')" \
    -F "CategoryId=$FIRST_CATEGORY_ID"

# 14. Create bundle — anonymous (→ 401)
assert_status POST "/api/bundles" 401 "POST /api/bundles (anonymous → 401)" \
    -F "Name=Test Bundle" \
    -F "Description=Description" \
    -F "Price=10.00" \
    -F "Status=Draft" \
    -F "ExpiresOn=2099-12-31T23:59:59Z" \
    -F "CategoryId=$FIRST_CATEGORY_ID"

# 15. Create bundle — participant role (→ 403)
assert_status POST "/api/bundles" 403 "POST /api/bundles (participant → 403)" \
    -H "$PARTICIPANT_AUTH" \
    -F "Name=Test Bundle" \
    -F "Description=Description" \
    -F "Price=10.00" \
    -F "Status=Draft" \
    -F "ExpiresOn=2099-12-31T23:59:59Z" \
    -F "CategoryId=$FIRST_CATEGORY_ID"

# 16. Create bundle — TRAINER role (should also succeed)
assert_status POST "/api/bundles" 201 "POST /api/bundles (TRAINER, valid → 201)" \
    -H "$TRAINER_AUTH" \
    -F "Name=Trainer Bundle" \
    -F "Description=A trainer's bundle" \
    -F "Price=29.99" \
    -F "Status=Draft" \
    -F "ExpiresOn=2099-12-31T23:59:59Z" \
    -F "CategoryId=$FIRST_CATEGORY_ID"

# 17. Create bundle — GYM role (should also succeed)
assert_status POST "/api/bundles" 201 "POST /api/bundles (GYM, valid → 201)" \
    -H "$GYM_AUTH" \
    -F "Name=Gym Bundle" \
    -F "Description=A gym's bundle" \
    -F "Price=99.99" \
    -F "Status=Published" \
    -F "ExpiresOn=2099-12-31T23:59:59Z" \
    -F "CategoryId=$FIRST_CATEGORY_ID"

# 18. Create bundle — invalid status (→ 400 validation)
assert_status POST "/api/bundles" 400 "POST /api/bundles (GYM_OWNER, invalid status → 400)" \
    -H "$GYM_OWNER_AUTH" \
    -F "Name=Bad Bundle" \
    -F "Description=Bad status" \
    -F "Price=10.00" \
    -F "Status=InvalidStatus" \
    -F "ExpiresOn=2099-12-31T23:59:59Z" \
    -F "CategoryId=$FIRST_CATEGORY_ID"

# 19. Create bundle — negative price (→ 400)
assert_status POST "/api/bundles" 400 "POST /api/bundles (negative price → 400)" \
    -H "$GYM_OWNER_AUTH" \
    -F "Name=Negative Bundle" \
    -F "Description=Negative price" \
    -F "Price=-5.00" \
    -F "Status=Draft" \
    -F "ExpiresOn=2099-12-31T23:59:59Z" \
    -F "CategoryId=$FIRST_CATEGORY_ID"

# 20. Create bundle — empty name (→ 400)
assert_status POST "/api/bundles" 400 "POST /api/bundles (empty name → 400)" \
    -H "$GYM_OWNER_AUTH" \
    -F "Name=" \
    -F "Description=Empty name" \
    -F "Price=10.00" \
    -F "Status=Draft" \
    -F "ExpiresOn=2099-12-31T23:59:59Z" \
    -F "CategoryId=$FIRST_CATEGORY_ID"

# 21. Get bundle by ID (public)
# Extract the bundle ID from the Location header of the first create
echo -n "  Fetching created bundle ID... "
BUNDLE_LOCATION=$(curl -s -i -X POST "$BASE_URL/api/bundles" \
    -H "$GYM_OWNER_AUTH" \
    -F "Name=Get-Test Bundle" \
    -F "Description=Bundle for get test" \
    -F "Price=15.00" \
    -F "Status=Draft" \
    -F "ExpiresOn=2099-12-31T23:59:59Z" \
    -F "CategoryId=$FIRST_CATEGORY_ID" 2>/dev/null | grep -i "^location:" | awk '{print $2}' | tr -d '\r\n')
CREATED_BUNDLE_ID=$(echo "$BUNDLE_LOCATION" | grep -oE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}')
echo "ID=$CREATED_BUNDLE_ID"

# 22. Get bundle by ID — valid
if [ -n "$CREATED_BUNDLE_ID" ]; then
    assert_body_contains GET "/api/bundles/$CREATED_BUNDLE_ID" "Get-Test Bundle" "GET /api/bundles/{id} (anonymous) returns bundle name"
fi

# 23. Get bundle by ID — non-existent (→ 404)
assert_status GET "/api/bundles/00000000-0000-0000-0000-000000000000" 404 "GET /api/bundles/nonexistent → 404"

# 24. Update bundle — own bundle (GYM_OWNER, valid)
if [ -n "$CREATED_BUNDLE_ID" ]; then
    assert_status PUT "/api/bundles/$CREATED_BUNDLE_ID" 204 "PUT /api/bundles/{id} (owner GYM_OWNER, update name → 204)" \
        -H "$GYM_OWNER_AUTH" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"Updated Bundle Name\"}"
fi

# 25. Update bundle — ADMIN can update someone else's bundle (→ 204)
if [ -n "$CREATED_BUNDLE_ID" ]; then
    assert_status PUT "/api/bundles/$CREATED_BUNDLE_ID" 204 "PUT /api/bundles/{id} (ADMIN updates other's bundle → 204)" \
        -H "$ADMIN_AUTH" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"Admin-Updated Bundle\"}"
fi

# 26. Update bundle — non-owner (TRAINER updates GYM_OWNER's bundle → 403)
if [ -n "$CREATED_BUNDLE_ID" ]; then
    assert_status PUT "/api/bundles/$CREATED_BUNDLE_ID" 403 "PUT /api/bundles/{id} (non-owner TRAINER → 403)" \
        -H "$TRAINER_AUTH" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"Hacker Bundle\"}"
fi

# 27. Update bundle — anonymous (→ 401)
if [ -n "$CREATED_BUNDLE_ID" ]; then
    assert_status PUT "/api/bundles/$CREATED_BUNDLE_ID" 401 "PUT /api/bundles/{id} (anonymous → 401)" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"Hacker Bundle\"}"
fi

# 28. Update bundle — non-existent (→ 404)
assert_status PUT "/api/bundles/00000000-0000-0000-0000-000000000000" 404 "PUT /api/bundles/nonexistent → 404" \
    -H "$GYM_OWNER_AUTH" \
    -H "Content-Type: application/json" \
    -d '{"name":"Nope"}'

# 29. Delete bundle — ADMIN can delete someone else's bundle (→ 204)
if [ -n "$CREATED_BUNDLE_ID" ]; then
    assert_status DELETE "/api/bundles/$CREATED_BUNDLE_ID" 204 "DELETE /api/bundles/{id} (ADMIN deletes other's bundle → 204)" \
        -H "$ADMIN_AUTH"
fi

# 30. Delete bundle — non-owner (TRAINER deletes GYM_OWNER's bundle → 403)
# Need a new bundle for this since the previous one was deleted by ADMIN
CREATED_BUNDLE_ID_2=$(curl -s -i -X POST "$BASE_URL/api/bundles" \
    -H "$GYM_OWNER_AUTH" \
    -F "Name=Second Test Bundle" \
    -F "Description=Bundle for delete auth tests" \
    -F "Price=25.00" \
    -F "Status=Draft" \
    -F "ExpiresOn=2099-12-31T23:59:59Z" \
    -F "CategoryId=$FIRST_CATEGORY_ID" 2>/dev/null | grep -i "^location:" | awk '{print $2}' | tr -d '\r\n' | grep -oE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}')
if [ -n "$CREATED_BUNDLE_ID_2" ]; then
    pass "Created second bundle for delete auth tests (ID=$CREATED_BUNDLE_ID_2)"
    assert_status DELETE "/api/bundles/$CREATED_BUNDLE_ID_2" 403 "DELETE /api/bundles/{id} (non-owner TRAINER → 403)" \
        -H "$TRAINER_AUTH"
fi

# 31. Delete bundle — anonymous (→ 401)
if [ -n "$CREATED_BUNDLE_ID_2" ]; then
    assert_status DELETE "/api/bundles/$CREATED_BUNDLE_ID_2" 401 "DELETE /api/bundles/{id} (anonymous → 401)"
fi

# 32. Delete bundle — owner (→ 204)
if [ -n "$CREATED_BUNDLE_ID_2" ]; then
    assert_status DELETE "/api/bundles/$CREATED_BUNDLE_ID_2" 204 "DELETE /api/bundles/{id} (owner GYM_OWNER → 204)"
fi

# 33. Delete bundle — already deleted (→ 404)
if [ -n "$CREATED_BUNDLE_ID_2" ]; then
    assert_status DELETE "/api/bundles/$CREATED_BUNDLE_ID_2" 404 "DELETE /api/bundles/{id} (already deleted → 404)" \
        -H "$GYM_OWNER_AUTH"
fi

# 34. Delete bundle — non-existent (→ 404)
assert_status DELETE "/api/bundles/00000000-0000-0000-0000-000000000000" 404 "DELETE /api/bundles/nonexistent → 404" \
    -H "$GYM_OWNER_AUTH"

# 35. Get bundle response includes actual category name (not empty)
# Create a bundle and check the category field in the response
CATEGORY_FOR_BUNDLE_ID=$FIRST_CATEGORY_ID
BUNDLE_FOR_CATEGORY_CHECK_ID=$(curl -s -i -X POST "$BASE_URL/api/bundles" \
    -H "$GYM_OWNER_AUTH" \
    -F "Name=Category Check Bundle" \
    -F "Description=Testing category name resolution" \
    -F "Price=35.00" \
    -F "Status=Draft" \
    -F "ExpiresOn=2099-12-31T23:59:59Z" \
    -F "CategoryId=$CATEGORY_FOR_BUNDLE_ID" 2>/dev/null | grep -i "^location:" | awk '{print $2}' | tr -d '\r\n' | grep -oE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}')
if [ -n "$BUNDLE_FOR_CATEGORY_CHECK_ID" ]; then
    CATEGORY_RESPONSE=$(curl -sf "$BASE_URL/api/bundles/$BUNDLE_FOR_CATEGORY_CHECK_ID")
    CATEGORY_NAME=$(echo "$CATEGORY_RESPONSE" | jq -r '.category // .Category // empty')
    if [ -n "$CATEGORY_NAME" ] && [ "$CATEGORY_NAME" != "null" ]; then
        pass "GET /api/bundles/{id} includes category name '$CATEGORY_NAME'"
    else
        fail "GET /api/bundles/{id} category name is empty or null - got: $(echo "$CATEGORY_RESPONSE" | head -c 300)"
    fi
    # Clean up
    curl -s -X DELETE "$BASE_URL/api/bundles/$BUNDLE_FOR_CATEGORY_CHECK_ID" -H "$GYM_OWNER_AUTH" > /dev/null
fi

# ─── Summary ─────────────────────────────────────────────────────────────
echo -e "\n${CYAN}══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  Test Summary${NC}"
echo -e "${CYAN}══════════════════════════════════════════════════════════${NC}"
echo -e "  ${GREEN}Passed: $PASS${NC}"
echo -e "  ${RED}Failed: $FAIL${NC}"
TOTAL=$((PASS+FAIL))
echo -e "  Total:  $TOTAL"

# Cleanup temp dir
rm -rf "$BUNDLE_IMAGES_DIR" "$TEST_DIR/services/Dumble.BundleManagementService/appsettings.Testing.json"

if [ "$FAIL" -eq 0 ]; then
    echo -e "\n  ${GREEN}All tests passed!${NC}"
else
    echo -e "\n  ${RED}Some tests failed.${NC}"
fi

echo ""
