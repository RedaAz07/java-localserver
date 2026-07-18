#!/usr/bin/env bash
# Tests.md #19-22: Redirection, Directory Listing (enabled/disabled), Default Index
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
preflight

# --- 19. Redirection -------------------------------------------------------
echo "# 19. Redirection"
run_test_header "GET /redirect returns a 301 with a Location header" \
    301 "Location" "" \
    "$BASE_URL/redirect"

run_test "GET /redirect with -L follows through to a 200" \
    200 "" \
    -L "$BASE_URL/redirect"
echo

# --- 20. Directory Listing Enabled --------------------------------------------
echo "# 20. Directory Listing Enabled"
# "/upload" on this server (8080) has directory_listing: true in config.json.
mkdir -p "$PROJECT_ROOT/uploads"
touch "$PROJECT_ROOT/uploads/.listing_probe"
run_test "GET /upload/ returns an HTML directory listing" \
    200 "<html" \
    "$BASE_URL/upload/"
rm -f "$PROJECT_ROOT/uploads/.listing_probe"
echo

# --- 21. Directory Listing Disabled --------------------------------------------
echo "# 21. Directory Listing Disabled"
# The second server (8081) has "/upload" with no directory_listing set at
# all and no default_file -- per handleGet(), that combination is what
# produces 403 rather than a listing or a 404.
DISABLED_URL="${DISABLED_URL:-http://127.0.0.1:8081/upload/}"
if curl -s -o /dev/null -m 5 "$DISABLED_URL"; then
    STATUS="$(curl -s -o /dev/null -m 15 -w "%{http_code}" "$DISABLED_URL")"
    if [ "$STATUS" = "403" ] || [ "$STATUS" = "404" ]; then
        pass "directory listing disabled correctly returns $STATUS"
    else
        fail "expected 403 or 404 with listing disabled, got $STATUS"
    fi
else
    skip "second server (8081) not reachable -- can't test disabled listing without it"
fi
echo

# --- 22. Default Index Page -----------------------------------------------------
echo "# 22. Default Index Page"
mkdir -p "$PROJECT_ROOT/www/testfolder"
echo "<html><body><h1>marker-$$-testfolder-index</h1></body></html>" > "$PROJECT_ROOT/www/testfolder/index.html"
run_test "GET /testfolder/ returns its index.html" \
    200 "marker-$$-testfolder-index" \
    "$BASE_URL/testfolder/"
rm -rf "$PROJECT_ROOT/www/testfolder"
echo

summary
