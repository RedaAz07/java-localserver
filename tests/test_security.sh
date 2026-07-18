#!/usr/bin/env bash
# Tests.md #39: Security Tests (directory traversal)
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
preflight

echo "# 39. Security Tests"

# --path-as-is stops curl from normalizing "../" locally before sending --
# without it, curl would collapse the path itself and we'd never actually
# test the server's own traversal protection.
STATUS="$(curl -s -o "$TMP_BODY" -m 15 -w "%{http_code}" --path-as-is "$BASE_URL/../../etc/passwd")"
if [ "$STATUS" = "403" ] || [ "$STATUS" = "404" ]; then
    if grep -q "root:" "$TMP_BODY" 2>/dev/null; then
        fail "status was $STATUS but /etc/passwd content leaked into the body anyway"
    else
        pass "directory traversal (../../etc/passwd) correctly blocked ($STATUS)"
    fi
else
    fail "directory traversal (../../etc/passwd) returned $STATUS instead of 403/404"
fi

STATUS="$(curl -s -o "$TMP_BODY" -m 15 -w "%{http_code}" --path-as-is "$BASE_URL/../../../secret.txt")"
if [ "$STATUS" = "403" ] || [ "$STATUS" = "404" ]; then
    pass "directory traversal (../../../secret.txt) correctly blocked ($STATUS)"
else
    fail "directory traversal (../../../secret.txt) returned $STATUS instead of 403/404"
fi

# URL-encoded variant -- some servers only guard the literal ".." pattern and
# miss the encoded form.
STATUS="$(curl -s -o "$TMP_BODY" -m 15 -w "%{http_code}" "$BASE_URL/%2e%2e/%2e%2e/etc/passwd")"
if [ "$STATUS" = "403" ] || [ "$STATUS" = "404" ]; then
    if grep -q "root:" "$TMP_BODY" 2>/dev/null; then
        fail "URL-encoded traversal: status was $STATUS but content leaked anyway"
    else
        pass "URL-encoded directory traversal correctly blocked ($STATUS)"
    fi
else
    fail "URL-encoded directory traversal returned $STATUS instead of 403/404"
fi

echo
summary
