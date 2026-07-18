#!/usr/bin/env bash
# Tests.md #23-26: Timeout, Chunked Transfer Encoding, Invalid Request, Malformed Headers
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
preflight

HOST="$(echo "$BASE_URL" | sed -E 's#https?://##' | cut -d: -f1)"
PORT="$(echo "$BASE_URL" | sed -E 's#https?://##' | cut -d: -f2)"

# --- 23. Timeout -----------------------------------------------------------
echo "# 23. Timeout"
if [ "${RUN_SLOW_TESTS:-0}" != "1" ]; then
    skip "connection-timeout test is slow/environment-dependent -- set RUN_SLOW_TESTS=1 to run it"
elif ! command -v nc >/dev/null 2>&1; then
    skip "nc (netcat) not installed"
else
    START=$(date +%s)
    (printf "GET /\r\n"; sleep 65) | nc -w 70 "$HOST" "$PORT" > /tmp/_timeout_out 2>&1
    END=$(date +%s)
    ELAPSED=$((END - START))
    if [ "$ELAPSED" -lt 65 ]; then
        pass "connection was closed automatically before the 65s idle mark (~${ELAPSED}s)"
    else
        fail "connection was not closed by the server within 65s"
    fi
fi
echo

# --- 24. Chunked Transfer Encoding --------------------------------------------
echo "# 24. Chunked Transfer Encoding"
FIXTURE="$(mktemp)"
echo "chunked body content" > "$FIXTURE"
curl -s -o "$TMP_BODY" -m 15 -w "%{http_code}" \
    -X POST \
    -H "Transfer-Encoding: chunked" \
    --data-binary "@$FIXTURE" \
    "$BASE_URL/upload/chunked_test.txt" > /tmp/_chunked_status
STATUS="$(cat /tmp/_chunked_status)"
if [ "$STATUS" = "200" ] || [ "$STATUS" = "201" ]; then
    if [ -f "$PROJECT_ROOT/uploads/chunked_test.txt" ] && grep -qF "chunked body content" "$PROJECT_ROOT/uploads/chunked_test.txt"; then
        pass "chunked request body correctly reconstructed"
    else
        fail "chunked upload returned $STATUS but content on disk doesn't match"
    fi
else
    fail "chunked transfer encoding (expected 200/201, got $STATUS)"
fi
rm -f "$FIXTURE" "$PROJECT_ROOT/uploads/chunked_test.txt"
echo

# --- 25. Invalid HTTP Request --------------------------------------------------
echo "# 25. Invalid HTTP Request"
if ! command -v nc >/dev/null 2>&1; then
    skip "nc (netcat) not installed"
else
    RESPONSE="$(printf "HELLO\r\n\r\n" | nc -w 3 "$HOST" "$PORT" 2>/dev/null)"
    if echo "$RESPONSE" | grep -q "400"; then
        pass "malformed request line returns 400"
    else
        fail "malformed request line did not return 400 (got: $(echo "$RESPONSE" | head -1))"
    fi
fi
echo

# --- 26. Malformed Headers ------------------------------------------------------
echo "# 26. Malformed Headers"
if ! command -v nc >/dev/null 2>&1; then
    skip "nc (netcat) not installed"
else
    RESPONSE="$(printf "GET / HTTP/1.1\r\nHost\r\n\r\n" | nc -w 3 "$HOST" "$PORT" 2>/dev/null)"
    if echo "$RESPONSE" | grep -q "400"; then
        pass "malformed header line returns 400"
    else
        fail "malformed header line did not return 400 (got: $(echo "$RESPONSE" | head -1))"
    fi
fi
echo

summary
