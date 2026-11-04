#!/usr/bin/env bash
# Steps 2 to 9 of section 13 of the spec of HU-CSH-001 (api), against a RUNNING container image of this service with the
# -db applied. Step 1 is `mvn -B verify`; step 10 is the counts in the README. Each step prints PASS or FAIL.
# Environment (names only, no value is kept here):
#   API_URL               default http://localhost:8085
#   JWT_PRIVATE_KEY_FILE  PEM private key whose public half the container reads as JWT_PUBLIC_KEY
#   MANAGE_CASH           the string the container reads as CASH_PERMISSION_MANAGE_CASH
set -u
API_URL="${API_URL:-http://localhost:8085}"
BASE="$API_URL/api/v1/cash-reporting"
: "${JWT_PRIVATE_KEY_FILE:?set the private key file}" "${MANAGE_CASH:?set the manage-cash permission string}"
fail=0; OUT=$(mktemp); trap 'rm -f "$OUT"' EXIT

b64() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
uuid() { cat /proc/sys/kernel/random/uuid 2>/dev/null || powershell -NoProfile -Command '[guid]::NewGuid().ToString()' | tr -d '\r'; }
token() { # tenant role [permission]
  local h c in perms=""; [ -n "${3:-}" ] && perms="\"$3\""
  h=$(printf '{"alg":"RS256","typ":"JWT"}' | b64)
  c=$(printf '{"sub":"%s","tenantId":"%s","roles":["%s"],"permissions":[%s],"exp":%s}' "$(uuid)" "$1" "$2" "$perms" "$(( $(date +%s) + 3600 ))" | b64)
  in="$h.$c"; printf '%s.%s' "$in" "$(printf '%s' "$in" | openssl dgst -sha256 -sign "$JWT_PRIVATE_KEY_FILE" | b64)"
}
call() { # method path token key body -> STATUS, BODY
  local args=(-s -o "$OUT" -w '%{http_code}' -X "$1" "$BASE$2" -H "Authorization: Bearer $3")
  [ -n "${4:-}" ] && args+=(-H "Idempotency-Key: $4"); [ -n "${5:-}" ] && args+=(-H 'Content-Type: application/json' -d "$5")
  STATUS=$(curl "${args[@]}"); BODY=$(cat "$OUT")
}
field() { printf '%s' "$BODY" | sed -n 's/.*"'"$1"'":\("[^"]*"\|[^,}]*\).*/\1/p' | tr -d '"' | head -1; }
check() { # step description, expected, actual
  if [ "$2" = "$3" ]; then echo "  ok    $1 ($3)"; else echo "  FAIL  $1: expected $2, got $3"; fail=1; fi
}

A=$(uuid); ADMIN=$(token "$A" TENANT_ADMINISTRATOR)
call GET /cash-days/current "$ADMIN"; DAY="/cash-days/$(field businessDate)"
EXPENSE='{"movementType":"OPERATIONAL_EXPENSE","paymentMedium":"CASH","concept":"Compra","amountCents":25000000}'
INCOME='{"movementType":"INCOME","paymentMedium":"TRANSFER","concept":"Venta","amountCents":129000000}'

echo "step 2 - open with a base, a cash expense, a transfer income, read the day"
call POST "$DAY/open" "$ADMIN" a-open-0001 '{"openingBaseCents":30000000}'; check "open" 200 "$STATUS"
call POST "$DAY/movements" "$ADMIN" a-expense-1 "$EXPENSE"; check "cash expense" 201 "$STATUS"
call POST "$DAY/movements" "$ADMIN" a-income-01 "$INCOME"; check "transfer income" 201 "$STATUS"
call GET "$DAY" "$ADMIN"; SESSION=$(field cashSessionId)
check "total = base + income - expenses" 134000000 "$(field totalCents)"
check "expected cash = base - cash expense" 5000000 "$(field expectedCashCents)"

echo "step 3 - repeat the opening, then close twice"
call POST "$DAY/open" "$ADMIN" a-open-0002 '{"openingBaseCents":1}'; check "second opening" 422 "$STATUS"; check "  error" ALREADY_OPEN "$(field error)"
call POST "$DAY/close" "$ADMIN" a-close-001; check "first closing" 200 "$STATUS"
call POST "$DAY/close" "$ADMIN" a-close-002; check "second closing" 422 "$STATUS"; check "  error" ALREADY_CLOSED "$(field error)"
call GET /cash-closures "$ADMIN"; CLOSURES=$(printf '%s' "$BODY" | grep -o '"badge"' | wc -l); check "one closure" 1 "$CLOSURES"

echo "step 4 - cash before the opening, cash on a closed day"
C=$(uuid); OTHER=$(token "$C" TENANT_ADMINISTRATOR)
call POST "$DAY/movements" "$OTHER" c-expense-1 "$EXPENSE"; check "cash before opening" 422 "$STATUS"; check "  error" NO_OPEN_CASH "$(field error)"
call POST "$DAY/movements" "$ADMIN" a-expense-2 "$EXPENSE"; check "cash on a closed day" 422 "$STATUS"; check "  error" ALREADY_CLOSED "$(field error)"

echo "step 5 - a transfer after the closing"
call POST "$DAY/movements" "$ADMIN" a-income-02 "$INCOME"; check "transfer after closing" 201 "$STATUS"; check "  afterClosing" true "$(field afterClosing)"
call GET "$DAY" "$ADMIN"; check "expected cash unchanged" 5000000 "$(field expectedCashCents)"

echo "step 6 - a transfer on a fresh tenant date"
call POST "$DAY/movements" "$OTHER" c-income-01 "$INCOME"; check "transfer creates the cash" 201 "$STATUS"
call GET "$DAY" "$OTHER"; check "expected cash is null (omitted or null)" none "$(case "$(field expectedCashCents)" in ""|null) echo none ;; *) echo present ;; esac)"

echo "step 7 - reopen without and with a reason"
call POST "$DAY/reopen" "$ADMIN" a-reopen-01 '{}'; check "reopen without reason" 400 "$STATUS"; check "  error" REASON_REQUIRED "$(field error)"
call POST "$DAY/reopen" "$ADMIN" a-reopen-02 '{"reason":"Falto un pago"}'; check "reopen with reason" 200 "$STATUS"
call GET "$DAY" "$ADMIN"; check "same cash" "$SESSION" "$(field cashSessionId)"; check "  status" OPEN "$(field status)"
call GET /cash-closures "$ADMIN"; check "closure kept" 1 "$(printf '%s' "$BODY" | grep -o '"badge"' | wc -l)"

echo "step 8 - each role against each operation (AC-X1)"
before=$fail; fail=0
roles="TENANT_ADMINISTRATOR OPERATIONAL_COLLABORATOR OPERATIONAL_COLLABORATOR+perm MANAGER ACCOUNTANT ANALYST FINAL_CUSTOMER PLATFORM_ADMINISTRATOR"
for entry in $roles; do
  role=${entry%%+*}; perm=""; [ "$entry" != "$role" ] && perm="$MANAGE_CASH"
  T=$(uuid); TOK=$(token "$T" "$role" "$perm"); n=0
  for op in "GET /cash-days read" "GET $DAY/movements read" "POST $DAY/open operate" "POST $DAY/close operate" "POST $DAY/reopen operate" \
            "POST $DAY/movements operate" "POST $DAY/adjust-base admin"; do
    set -- $op; n=$((n+1)); call "$1" "$2" "$TOK" "k-$role-$n-0001" '{"openingBaseCents":1,"reason":"x"}'
    case "$role:$3:$perm" in
      TENANT_ADMINISTRATOR:*) want=allowed ;; OPERATIONAL_COLLABORATOR:read:*) want=allowed ;;
      OPERATIONAL_COLLABORATOR:operate:?*) want=allowed ;; MANAGER:read:*|ACCOUNTANT:read:*|ANALYST:read:*) want=allowed ;;
      *) want=forbidden ;;
    esac
    got=allowed; [ "$STATUS" = 403 ] && got=forbidden
    [ "$want" = "$got" ] || { echo "  FAIL  $entry $1 $2: wanted $want, got $STATUS"; fail=1; }
  done
done
[ "$fail" = 0 ] && echo "  ok    8 roles x 7 operations follow the matrix"; [ "$before" = 1 ] && fail=1

echo "step 9 - the same key twice, the same key from another tenant"
E=$(uuid); EA=$(token "$E" TENANT_ADMINISTRATOR); F=$(uuid); FB=$(token "$F" TENANT_ADMINISTRATOR)
call POST "$DAY/open" "$EA" shared-key-001 '{"openingBaseCents":100}'; first=$(field cashSessionId); check "first use" 200 "$STATUS"
call POST "$DAY/open" "$EA" shared-key-001 '{"openingBaseCents":100}'; check "same key again" 200 "$STATUS"; check "  same result" "$first" "$(field cashSessionId)"
call POST "$DAY/open" "$FB" shared-key-001 '{"openingBaseCents":100}'; check "same key, tenant B" 200 "$STATUS"
[ "$(field cashSessionId)" != "$first" ] && echo "  ok    tenant B got its own cash" || { echo "  FAIL  tenant B received tenant A's result"; fail=1; }

[ "$fail" = 0 ] && echo "DONE: steps 2 to 9 passed" || echo "DONE: failures above"
exit "$fail"
