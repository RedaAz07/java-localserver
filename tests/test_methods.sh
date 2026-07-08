#!/usr/bin/env bash
# Tests.md #7-12: 403, 405, POST, POST JSON, DELETE, DELETE missing file
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
preflight

# This project's second server (8081/8082) has a "/upload" route with no
# default_file and no directory_listing set -- per ResponseBuilder.handleGet(),
# that's exactly the combination that produces 403 for a directory request
# (default_file would short-circuit to 404 instead if it were set). We reuse
# that route rather than inventing a new fixture folder + route.
FORBIDDEN_URL="${FORBIDDEN_URL:-http://127.0.0.1:8081/upload/}"

# --- 7. Forbidden Access -----------------------------------------------------
echo "# 7. Forbidden Access"
if curl -s -o /dev/null -m 5 "$FORBIDDEN_URL"; then
    run_test "directory with no listing/index returns 403" \
        403 "" \
        "$FORBIDDEN_URL"
else
    skip "second server (8081) not reachable -- can't test 403 without it"
fi
echo

# --- 8. Unsupported HTTP Method ----------------------------------------------
echo "# 8. Unsupported HTTP Method"
run_test "PUT to a GET/POST/DELETE-only route returns 405" \
    405 "" \
    -X PUT "$BASE_URL/index.html"
echo

# --- 9. POST Request ----------------------------------------------------------
echo "# 9. POST Request"
curl -s -o "$TMP_BODY" -m 15 -w "%{http_code}" -X POST \
    -d "username=Ahmed&password=1234" \
    "$BASE_URL/upload/logintest.txt" > /tmp/_post_status
STATUS="$(cat /tmp/_post_status)"
if [ "$STATUS" = "200" ] || [ "$STATUS" = "201" ]; then
    if [ -f "$PROJECT_ROOT/uploads/logintest.txt" ] && grep -qF "username=Ahmed" "$PROJECT_ROOT/uploads/logintest.txt"; then
        pass "POST body correctly received and written to disk"
    else
        fail "POST returned $STATUS but uploaded file content doesn't match what was sent"
    fi
else
    fail "POST request (expected 200/201, got $STATUS)"
fi
echo

# --- 10. POST JSON -------------------------------------------------------------
echo "# 10. POST JSON"
curl -s -o "$TMP_BODY" -m 15 -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d '{"name":"Ahmed"}' \
    "$BASE_URL/upload/data.json" > /tmp/_json_status
STATUS="$(cat /tmp/_json_status)"
if [ "$STATUS" = "200" ] || [ "$STATUS" = "201" ]; then
    if [ -f "$PROJECT_ROOT/uploads/data.json" ] && grep -qF '"name":"Ahmed"' "$PROJECT_ROOT/uploads/data.json"; then
        pass "server receives JSON body correctly"
    else
        fail "POST returned $STATUS but uploaded JSON content doesn't match what was sent"
    fi
else
    fail "POST JSON request (expected 200/201, got $STATUS)"
fi
echo

# --- 11. DELETE Request ---------------------------------------------------------
echo "# 11. DELETE Request"
echo "delete me" > "$PROJECT_ROOT/uploads/delete_test.txt"
run_test "DELETE an existing file returns 200" \
    200 "" \
    -X DELETE "$BASE_URL/upload/delete_test.txt"
if [ -f "$PROJECT_ROOT/uploads/delete_test.txt" ]; then
    fail "file still exists on disk after DELETE"
else
    pass "file actually removed from disk"
fi
echo

# --- 12. DELETE Missing File -----------------------------------------------------
echo "# 12. DELETE Missing File"
run_test "DELETE a nonexistent file returns 404" \
    404 "" \
    -X DELETE "$BASE_URL/upload/does_not_exist_xyz.txt"
echo

# cleanup
rm -f "$PROJECT_ROOT/uploads/logintest.txt" "$PROJECT_ROOT/uploads/data.json"

summary
