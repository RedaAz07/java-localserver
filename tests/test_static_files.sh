#!/usr/bin/env bash
# Tests.md #2-6: GET request, GET image, GET CSS, GET JavaScript, 404
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
preflight

WWW_DIR="$PROJECT_ROOT/www"

# --- fixtures: only create what's missing, never touch existing content ----
if [ ! -f "$WWW_DIR/style.css" ]; then
    echo "body { font-family: sans-serif; }" > "$WWW_DIR/style.css"
fi
if [ ! -f "$WWW_DIR/app.js" ]; then
    echo "console.log('hello');" > "$WWW_DIR/app.js"
fi

# --- 2. GET Request -----------------------------------------------------
echo "# 2. GET Request"
run_test "GET /index.html returns 200 with HTML" \
    200 "<html" \
    "$BASE_URL/index.html"
echo

# --- 3. GET Image --------------------------------------------------------
echo "# 3. GET Image"
if [ -f "$WWW_DIR/khalid.png" ]; then
    curl -s -o "$TMP_BODY" -m 15 "$BASE_URL/khalid.png"
    if diff -q "$WWW_DIR/khalid.png" "$TMP_BODY" >/dev/null 2>&1; then
        pass "downloaded image is byte-identical to the original"
    else
        fail "downloaded image differs from the original file on disk"
    fi
else
    skip "no image fixture found under www/ to test"
fi
echo

# --- 4. GET CSS ------------------------------------------------------------
echo "# 4. GET CSS"
run_test_header "style.css has correct Content-Type" \
    200 "Content-Type" "text/css" \
    "$BASE_URL/style.css"
echo

# --- 5. GET JavaScript -------------------------------------------------------
echo "# 5. GET JavaScript"
run_test_header "app.js has correct Content-Type" \
    200 "Content-Type" "javascript" \
    "$BASE_URL/app.js"
echo

# --- 6. GET Non-existing File ------------------------------------------------
echo "# 6. GET Non-existing File"
curl -s -o "$TMP_BODY" -D "$TMP_HEADERS" -m 15 -w "%{http_code}" "$BASE_URL/unknown-file-xyz.html" > /tmp/_status
STATUS="$(cat /tmp/_status)"
if [ "$STATUS" != "404" ]; then
    fail "GET missing file returns 404 (got $STATUS)"
elif error_page_matches 404; then
    pass "GET missing file returns 404 with the actual custom error page"
else
    fail "GET missing file returned 404, but body didn't match error_pages/404.html"
fi
echo

summary
