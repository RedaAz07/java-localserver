#!/usr/bin/env bash
# Tests.md #35: Custom Error Pages (400, 403, 404, 405, 413, 500)
# Each check reads the real error_pages/<code>.html off disk and compares the
# response body against it byte-for-byte, rather than guessing at wording.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
preflight

HOST="$(echo "$BASE_URL" | sed -E 's#https?://##' | cut -d: -f1)"
PORT="$(echo "$BASE_URL" | sed -E 's#https?://##' | cut -d: -f2)"

check_error_page() {
    local code="$1"
    local status="$2"
    if [ "$status" != "$code" ]; then
        fail "$code trigger (expected status $code, got $status)"
    elif error_page_matches "$code"; then
        pass "$code returns the actual custom error page"
    else
        fail "$code trigger returned status $code, but body didn't match error_pages/${code}.html"
    fi
}

echo "# 35. Custom Error Pages"

# 400: malformed request line
if command -v nc >/dev/null 2>&1; then
    printf "HELLO\r\n\r\n" | nc -w 3 "$HOST" "$PORT" > /tmp/_raw_400 2>/dev/null
    # Split off the body (after the blank line) into TMP_BODY for error_page_matches
    awk 'body{print} /^\r?$/{body=1}' /tmp/_raw_400 > "$TMP_BODY"
    STATUS_LINE="$(head -1 /tmp/_raw_400)"
    if echo "$STATUS_LINE" | grep -q "400"; then
        check_error_page 400 400
    else
        fail "400 trigger (status line: $STATUS_LINE)"
    fi
else
    skip "400 (nc not installed)"
fi

# 403: reuse the same disabled-listing route as test_routing.sh #21
DISABLED_URL="${DISABLED_URL:-http://127.0.0.1:8081/upload/}"
if curl -s -o /dev/null -m 5 "$DISABLED_URL"; then
    STATUS="$(curl -s -o "$TMP_BODY" -m 15 -w "%{http_code}" "$DISABLED_URL")"
    check_error_page 403 "$STATUS"
else
    skip "403 (second server 8081 not reachable)"
fi

# 404: nonexistent file
STATUS="$(curl -s -o "$TMP_BODY" -m 15 -w "%{http_code}" "$BASE_URL/does-not-exist-xyz.html")"
check_error_page 404 "$STATUS"

# 405: method not allowed
STATUS="$(curl -s -o "$TMP_BODY" -m 15 -w "%{http_code}" -X PUT "$BASE_URL/index.html")"
check_error_page 405 "$STATUS"

# 413: body over the configured limit
FIXTURE="$(mktemp)"
dd if=/dev/zero of="$FIXTURE" bs=1M count=8 >/dev/null 2>&1
STATUS="$(curl -s -o "$TMP_BODY" -m 30 -w "%{http_code}" --data-binary "@$FIXTURE" "$BASE_URL/oversized.bin")"
rm -f "$FIXTURE" "$PROJECT_ROOT/www/oversized.bin"
check_error_page 413 "$STATUS"

# 500: CGI script crash (reuses cgi/error.py from the CGI test suite)
if [ -f "$PROJECT_ROOT/cgi/error.py" ]; then
    STATUS="$(curl -s -o "$TMP_BODY" -m 15 -w "%{http_code}" "$BASE_URL/cgi/error.py")"
    check_error_page 500 "$STATUS"
else
    skip "500 (cgi/error.py fixture not present -- run tests/test_cgi.sh first)"
fi

echo
summary
