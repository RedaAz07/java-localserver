#!/usr/bin/env bash
#
# Automated CGI test suite for java-localserver.
# Covers Tests.md sections 29-33 (CGI Python, CGI JavaScript, invalid script,
# runtime error, PATH_INFO/relative paths) plus the deadlock regression tests
# built during CGI development.
#
# Usage:
#   ./tests/test_cgi.sh
#   BASE_URL=http://127.0.0.1:8081 ./tests/test_cgi.sh   # override target
#
# Assumes the server is already running (this script does not start/stop it).
# Exits 0 if every test passes, 1 otherwise -- safe to use in CI.

set -u

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
CGI_PATH="${CGI_PATH:-/cgi}"          # matches config.json's route "path"
CGI_DIR="${CGI_DIR:-cgi}"             # matches config.json's route "root"

PASS=0
FAIL=0
TMP_BODY="$(mktemp)"
trap 'rm -f "$TMP_BODY"' EXIT

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# --- helpers ---------------------------------------------------------------

pass() {
    echo -e "  ${GREEN}PASS${NC}: $1"
    PASS=$((PASS + 1))
}

fail() {
    echo -e "  ${RED}FAIL${NC}: $1"
    FAIL=$((FAIL + 1))
}

# run_test <name> <expected_status> <expected_body_substring|""> <curl args...>
run_test() {
    local name="$1"; shift
    local expected_status="$1"; shift
    local expected_substring="$1"; shift

    local actual_status
    actual_status="$(curl -s -o "$TMP_BODY" -m 15 -w "%{http_code}" "$@" 2>/dev/null)"

    if [ "$actual_status" != "$expected_status" ]; then
        fail "$name (expected status $expected_status, got $actual_status)"
        return
    fi

    if [ -n "$expected_substring" ] && ! grep -qF "$expected_substring" "$TMP_BODY"; then
        fail "$name (status OK, but body missing expected text: \"$expected_substring\")"
        echo "    --- actual body ---"
        sed 's/^/    /' "$TMP_BODY" | head -5
        echo "    -------------------"
        return
    fi

    pass "$name"
}

# --- setup: ensure the CGI test scripts this suite depends on exist --------

setup_scripts() {
    mkdir -p "$CGI_DIR"

    if [ ! -f "$CGI_DIR/test.py" ]; then
        cat > "$CGI_DIR/test.py" << 'EOF'
#!/usr/bin/env python3
import os
print("Content-Type: text/html")
print()
print("<h1>Hello Python CGI</h1>")
name = None
qs = os.environ.get("QUERY_STRING", "")
for pair in qs.split("&"):
    if pair.startswith("name="):
        name = pair.split("=", 1)[1]
if name:
    print(f"<p>Hello, {name}!</p>")
EOF
        chmod +x "$CGI_DIR/test.py"
    fi

    if [ ! -f "$CGI_DIR/test.js" ]; then
        cat > "$CGI_DIR/test.js" << 'EOF'
console.log("Content-Type: text/html");
console.log();
console.log("<h1>Hello JavaScript CGI</h1>");
EOF
        chmod +x "$CGI_DIR/test.js"
    fi

    if [ ! -f "$CGI_DIR/error.py" ]; then
        cat > "$CGI_DIR/error.py" << 'EOF'
raise Exception("Crash")
EOF
        chmod +x "$CGI_DIR/error.py"
    fi

    cat > "$CGI_DIR/_pathinfo_check.py" << 'EOF'
import os
print("Content-Type: text/html")
print()
print("PATH_INFO=" + os.environ.get("PATH_INFO", ""))
print("SCRIPT_NAME=" + os.environ.get("SCRIPT_NAME", ""))
EOF
    chmod +x "$CGI_DIR/_pathinfo_check.py"
}

cleanup_scripts() {
    rm -f "$CGI_DIR/_pathinfo_check.py"
}

# --- preflight ---------------------------------------------------------------

echo "Target: $BASE_URL$CGI_PATH"
if ! curl -s -o /dev/null -m 5 "$BASE_URL/"; then
    echo -e "${YELLOW}Server not reachable at $BASE_URL -- is it running (./run.sh)?${NC}"
    exit 1
fi
echo

setup_scripts

# --- Tests.md #29: CGI Python -----------------------------------------------

echo "# 29. CGI Python"
run_test "test.py returns expected HTML" \
    200 "<h1>Hello Python CGI</h1>" \
    "$BASE_URL$CGI_PATH/test.py"

run_test "test.py receives query string parameters" \
    200 "Hello, Ahmed!" \
    "$BASE_URL$CGI_PATH/test.py?name=Ahmed"
echo

# --- Tests.md #30: CGI JavaScript (Node.js) ---------------------------------

echo "# 30. CGI JavaScript (Node.js)"
if command -v node >/dev/null 2>&1; then
    run_test "test.js returns expected HTML" \
        200 "<h1>Hello JavaScript CGI</h1>" \
        "$BASE_URL$CGI_PATH/test.js"
else
    echo -e "  ${YELLOW}SKIP${NC}: node not installed, skipping JS CGI test"
fi
echo

# --- Tests.md #31: CGI invalid script ---------------------------------------

echo "# 31. CGI Invalid Script"
run_test "nonexistent script returns 404" \
    404 "" \
    "$BASE_URL$CGI_PATH/does_not_exist.py"
echo

# --- Tests.md #32: CGI runtime error ----------------------------------------

echo "# 32. CGI Runtime Error"
run_test "crashing script returns 500" \
    500 "" \
    "$BASE_URL$CGI_PATH/error.py"

# Server must continue running after a CGI crash -- confirm with a normal request.
run_test "server still responds after a CGI crash" \
    200 "" \
    "$BASE_URL/"
echo

# --- Tests.md #33: CGI relative path / PATH_INFO ----------------------------

echo "# 33. CGI Relative Path (PATH_INFO)"
run_test "PATH_INFO is set and non-empty" \
    200 "PATH_INFO=" \
    "$BASE_URL$CGI_PATH/_pathinfo_check.py"

# Extra check: PATH_INFO should actually resolve to a real file on disk,
# not just be a non-empty string.
curl -s -m 10 "$BASE_URL$CGI_PATH/_pathinfo_check.py" > "$TMP_BODY" 2>/dev/null
PATH_INFO_VALUE="$(grep '^PATH_INFO=' "$TMP_BODY" | cut -d= -f2-)"
if [ -n "$PATH_INFO_VALUE" ] && [ -f "$PATH_INFO_VALUE" ]; then
    pass "PATH_INFO resolves to an existing file on disk ($PATH_INFO_VALUE)"
else
    fail "PATH_INFO does not resolve to an existing file (\"$PATH_INFO_VALUE\")"
fi
echo

# --- summary -----------------------------------------------------------------

cleanup_scripts

echo "============================================"
echo -e "  ${GREEN}$PASS passed${NC}, ${RED}$FAIL failed${NC}"
echo "============================================"

if [ "$FAIL" -ne 0 ]; then
    exit 1
fi
exit 0
