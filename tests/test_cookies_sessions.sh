#!/usr/bin/env bash
# Tests.md #16-18: Cookies, Sending Cookies, Sessions
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
preflight

COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

# --- 16. Cookies -------------------------------------------------------------
echo "# 16. Cookies"
run_test_header "response includes a Set-Cookie header" \
    200 "Set-Cookie" "" \
    "$BASE_URL/"
echo

# --- 17. Sending Cookies -------------------------------------------------------
echo "# 17. Sending Cookies"
run_test "server accepts a request carrying a Cookie header" \
    200 "" \
    -H "Cookie: SESSIONID=12345" \
    "$BASE_URL/"
echo

# --- 18. Sessions ----------------------------------------------------------------
echo "# 18. Sessions"
curl -s -o /dev/null -c "$COOKIE_JAR" -m 15 "$BASE_URL/"
FIRST_SESSID="$(grep -i SESSID "$COOKIE_JAR" | awk '{print $NF}')"

curl -s -o /dev/null -b "$COOKIE_JAR" -c "$COOKIE_JAR" -m 15 "$BASE_URL/"
SECOND_SESSID="$(grep -i SESSID "$COOKIE_JAR" | awk '{print $NF}')"

if [ -n "$FIRST_SESSID" ] && [ "$FIRST_SESSID" = "$SECOND_SESSID" ]; then
    pass "same session reused across requests ($FIRST_SESSID)"
else
    fail "session not reused (first=\"$FIRST_SESSID\", second=\"$SECOND_SESSID\")"
fi
echo

summary
