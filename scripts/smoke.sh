#!/usr/bin/env bash
set -euo pipefail
AUTH="developer:${LOCAL_PASSWORD:-local-only-change-me}"
# Run the four services mentioned in README before this script.
curl --fail-with-body -sS -u "$AUTH" http://localhost:8086/local/v1/instruments -H 'Content-Type: application/json' -d '{"provider":"CIELO","affiliationId":"demo"}' > /tmp/gateway-token.json
TOKEN=$(python3 -c 'import json;print(json.load(open("/tmp/gateway-token.json"))["token"])')
BODY=$(python3 -c 'import json,sys;print(json.dumps({"merchantOrderId":"DEMO-1","amount":15990,"currency":"BRL","instrumentToken":sys.argv[1]}))' "$TOKEN")
curl --fail-with-body -sS -u "$AUTH" http://localhost:8084/v1/payments -H 'Content-Type: application/json' -H "Idempotency-Key: demo-$TOKEN" -d "$BODY"
printf '\n'
curl --fail-with-body -sS -u "$AUTH" http://localhost:8087/internal/v1/assessments -H 'Content-Type: application/json' -d '{"paymentId":"22222222-2222-2222-2222-222222222222","amount":15990,"attemptsLastHour":1,"declinesLastHour":0,"knownCompromised":false}'
printf '\nPayment remains CREATED. No financial orchestration is enabled in this scaffold.\n'
