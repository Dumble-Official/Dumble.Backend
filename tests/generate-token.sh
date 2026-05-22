#!/bin/bash
# Generates a JWT token for testing .NET endpoints
# Uses the same secret as the test docker-compose environment

SECRET_B64="dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhtYWMtc2hhMjU2LXZhbGlkYXRpb24="

# Header: {"alg":"HS256","typ":"JWT"}
HEADER=$(echo -n '{"alg":"HS256","typ":"JWT"}' | openssl base64 -e | tr '+/' '-_' | tr -d '=\n')

# Payload with test claims - exp set far in the future
NOW=$(date +%s)
EXP=$((NOW + 86400))  # 24h from now

# Regular user token
USER_PAYLOAD=$(echo -n "{\"sub\":\"test-user-1\",\"userId\":\"test-user-1\",\"displayName\":\"Test User\",\"profileImage\":\"https://example.com/avatar.jpg\",\"roles\":[\"USER\"],\"iat\":$NOW,\"exp\":$EXP}" | openssl base64 -e | tr '+/' '-_' | tr -d '=\n')

# Admin token
ADMIN_PAYLOAD=$(echo -n "{\"sub\":\"test-admin-1\",\"userId\":\"test-admin-1\",\"displayName\":\"Test Admin\",\"profileImage\":\"https://example.com/admin.jpg\",\"roles\":[\"ADMIN\"],\"iat\":$NOW,\"exp\":$EXP}" | openssl base64 -e | tr '+/' '-_' | tr -d '=\n')

# Gym owner token
GYM_OWNER_PAYLOAD=$(echo -n "{\"sub\":\"test-gym-owner-1\",\"userId\":\"test-gym-owner-1\",\"displayName\":\"Test Gym Owner\",\"profileImage\":\"https://example.com/gym.jpg\",\"roles\":[\"GYM_OWNER\"],\"iat\":$NOW,\"exp\":$EXP}" | openssl base64 -e | tr '+/' '-_' | tr -d '=\n')

# Second user for social interactions
USER2_PAYLOAD=$(echo -n "{\"sub\":\"test-user-2\",\"userId\":\"test-user-2\",\"displayName\":\"Test User 2\",\"profileImage\":\"https://example.com/avatar2.jpg\",\"roles\":[\"USER\"],\"iat\":$NOW,\"exp\":$EXP}" | openssl base64 -e | tr '+/' '-_' | tr -d '=\n')

# Decode secret from base64 for HMAC signing
SECRET_RAW=$(echo -n "$SECRET_B64" | openssl base64 -d)

sign_token() {
    local header_payload="$1"
    echo -n "$header_payload" | openssl dgst -sha256 -hmac "$SECRET_RAW" -binary | openssl base64 -e | tr '+/' '-_' | tr -d '=\n'
}

# Generate tokens
USER_SIG=$(sign_token "$HEADER.$USER_PAYLOAD")
USER_TOKEN="$HEADER.$USER_PAYLOAD.$USER_SIG"

ADMIN_SIG=$(sign_token "$HEADER.$ADMIN_PAYLOAD")
ADMIN_TOKEN="$HEADER.$ADMIN_PAYLOAD.$ADMIN_SIG"

GYM_OWNER_SIG=$(sign_token "$HEADER.$GYM_OWNER_PAYLOAD")
GYM_OWNER_TOKEN="$HEADER.$GYM_OWNER_PAYLOAD.$GYM_OWNER_SIG"

USER2_SIG=$(sign_token "$HEADER.$USER2_PAYLOAD")
USER2_TOKEN="$HEADER.$USER2_PAYLOAD.$USER2_SIG"

echo "USER_TOKEN=$USER_TOKEN"
echo "ADMIN_TOKEN=$ADMIN_TOKEN"
echo "GYM_OWNER_TOKEN=$GYM_OWNER_TOKEN"
echo "USER2_TOKEN=$USER2_TOKEN"
