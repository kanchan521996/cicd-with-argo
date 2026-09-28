#!/usr/bin/env bash
# End-to-end API smoke test. Exercises every payment flow against a running deployment.
#   ./scripts/smoke-test.sh                      # defaults to http://localhost:3000 (through the frontend nginx)
#   ./scripts/smoke-test.sh https://paylane.example.com
# Requires: curl, jq
set -euo pipefail

BASE="${1:-http://localhost:3000}"
BASE="${BASE%/}"
RUN=$(date +%s)
PASS="Passw0rd!$RUN"
PIN="1234"
pass=0

command -v jq >/dev/null || { echo "jq is required (brew install jq / apt install jq)"; exit 1; }

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
ok()   { pass=$((pass+1)); printf '  \033[32m✔\033[0m %s\n' "$*"; }
fail() { printf '  \033[31m✘ %s\033[0m\n' "$*"; exit 1; }

# call METHOD PATH [TOKEN] [JSON_BODY]  -> prints body, fails on non-2xx
call() {
  local method=$1 path=$2 token=${3:-} body=${4:-}
  local args=(-sS -X "$method" "$BASE$path" -H 'Accept: application/json' -w '\n%{http_code}')
  [[ -n $token ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n $body ]] && args+=(-H 'Content-Type: application/json' -H "Idempotency-Key: smoke-$RUN-$RANDOM$RANDOM" -d "$body")
  local out code
  out=$(curl "${args[@]}")
  code=${out##*$'\n'}
  out=${out%$'\n'*}
  if [[ $code != 2* ]]; then fail "$method $path -> HTTP $code: $out"; fi
  printf '%s' "$out"
}

say "Health"
CODE=$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/api/billers") || fail "cannot reach $BASE"
[[ $CODE == 401 ]] && ok "API reachable and protected (401 without token)" || fail "GET /api/billers without token returned $CODE, expected 401"

say "Register two users"
A=$(call POST /api/auth/register "" "{\"fullName\":\"Alice Smoke\",\"email\":\"alice$RUN@test.local\",\"phone\":\"+1555${RUN: -7}\",\"password\":\"$PASS\"}")
TA=$(jq -r .token <<<"$A"); ok "alice registered"
B=$(call POST /api/auth/register "" "{\"fullName\":\"Bob Smoke\",\"email\":\"bob$RUN@test.local\",\"phone\":\"+1666${RUN: -7}\",\"password\":\"$PASS\"}")
TB=$(jq -r .token <<<"$B"); ok "bob registered"
TA=$(jq -r .token <<<"$(call POST /api/auth/login "" "{\"email\":\"alice$RUN@test.local\",\"password\":\"$PASS\"}")"); ok "alice can log in"

say "Set transaction PINs"
call POST /api/users/me/pin "$TA" "{\"password\":\"$PASS\",\"pin\":\"$PIN\"}" >/dev/null; ok "alice PIN set"
call POST /api/users/me/pin "$TB" "{\"password\":\"$PASS\",\"pin\":\"$PIN\"}" >/dev/null; ok "bob PIN set"

say "Cards, banks, top-up"
CARD=$(jq -r .id <<<"$(call POST /api/payment-methods/cards "$TA" '{"cardNumber":"4242424242424242","holderName":"Alice Smoke","expiryMonth":12,"expiryYear":2030,"cvv":"123"}')"); ok "card added (id $CARD)"
TX=$(call POST /api/wallet/topup "$TA" "{\"paymentMethodId\":$CARD,\"amount\":500}")
[[ $(jq -r .status <<<"$TX") == COMPLETED ]] && ok "topped up 500 ($(jq -r .reference <<<"$TX"))" || fail "top-up: $TX"
BAD=$(jq -r .id <<<"$(call POST /api/payment-methods/cards "$TA" '{"cardNumber":"4000000000000002","holderName":"Alice Smoke","expiryMonth":12,"expiryYear":2030,"cvv":"123"}')")
TX=$(call POST /api/wallet/topup "$TA" "{\"paymentMethodId\":$BAD,\"amount\":50}")
[[ $(jq -r .status <<<"$TX") == FAILED ]] && ok "declined card recorded as FAILED" || fail "expected decline: $TX"

say "Transfer"
call GET "/api/users/lookup?query=bob$RUN@test.local" "$TA" >/dev/null; ok "recipient lookup"
TX=$(call POST /api/transfers "$TA" "{\"recipient\":\"bob$RUN@test.local\",\"amount\":100,\"note\":\"smoke test\",\"pin\":\"$PIN\"}")
ok "alice sent bob 100 ($(jq -r .reference <<<"$TX"))"

say "Money request"
RID=$(jq -r .id <<<"$(call POST /api/requests "$TB" "{\"payer\":\"alice$RUN@test.local\",\"amount\":20,\"note\":\"coffee\"}")"); ok "bob requested 20"
[[ $(jq -r .pending <<<"$(call GET /api/requests/pending-count "$TA")") -ge 1 ]] && ok "alice sees pending request"
call POST "/api/requests/$RID/pay" "$TA" "{\"pin\":\"$PIN\"}" >/dev/null; ok "alice paid the request"

say "Bill payment"
BILLER=$(jq -r '.[0].id' <<<"$(call GET /api/billers "$TA")")
call POST /api/bills/pay "$TA" "{\"billerId\":$BILLER,\"accountReference\":\"ACC-12345\",\"amount\":30,\"pin\":\"$PIN\"}" >/dev/null; ok "bill paid"

say "Withdraw"
BANK=$(jq -r .id <<<"$(call POST /api/payment-methods/banks "$TB" '{"bankName":"Test Bank","holderName":"Bob Smoke","accountNumber":"12345678","routingCode":"TEST0001"}')"); ok "bob linked bank"
call POST /api/wallet/withdraw "$TB" "{\"paymentMethodId\":$BANK,\"amount\":50,\"pin\":\"$PIN\"}" >/dev/null; ok "bob withdrew 50"

say "Balances"
BA=$(jq -r .balance <<<"$(call GET /api/wallet "$TA")"); BB=$(jq -r .balance <<<"$(call GET /api/wallet "$TB")")
# alice: 500 - 100 - 20 - 30 = 350 ; bob: 100 + 20 - 50 = 70
[[ $(printf '%.2f' "$BA") == 350.00 ]] && ok "alice balance 350.00" || fail "alice balance $BA (expected 350.00)"
[[ $(printf '%.2f' "$BB") == 70.00 ]] && ok "bob balance 70.00" || fail "bob balance $BB (expected 70.00)"
call GET "/api/transactions?size=5" "$TA" >/dev/null; ok "history loads"
[[ $(jq -r .unread <<<"$(call GET /api/notifications/unread-count "$TB")") -ge 1 ]] && ok "bob has notifications"

say "Admin"
ADMIN_EMAIL=${ADMIN_EMAIL:-admin@paylane.local}; ADMIN_PASSWORD=${ADMIN_PASSWORD:-Admin@12345}
TADM=$(jq -r .token <<<"$(call POST /api/auth/login "" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")"); ok "admin login"
call GET /api/admin/stats "$TADM" >/dev/null; ok "admin stats"
REF2=$(jq -r .reference <<<"$(call POST /api/transfers "$TA" "{\"recipient\":\"bob$RUN@test.local\",\"amount\":10,\"note\":\"oops\",\"pin\":\"$PIN\"}")")
call POST "/api/admin/transactions/$REF2/reverse" "$TADM" '{"reason":"smoke test reversal"}' >/dev/null; ok "admin reversed a 10 transfer ($REF2)"
BA=$(jq -r .balance <<<"$(call GET /api/wallet "$TA")")
[[ $(printf '%.2f' "$BA") == 350.00 ]] && ok "alice refunded back to 350.00" || fail "alice balance after reversal $BA"

printf '\n\033[32mAll %d checks passed against %s\033[0m\n' "$pass" "$BASE"
