#!/usr/bin/env bash
#
# Shared helpers for java-localserver test scripts.
# Source this from a test_*.sh file; do not run it directly.
#
# Conventions used by every test_*.sh:
#   - BASE_URL points at the primary server (default 127.0.0.1:8080)
#   - PASS/FAIL/SKIP counters are printed by summary() at the end
#   - Scripts exit 0 if FAIL == 0, exit 1 otherwise (usable as a CI gate)
#   - PROJECT_ROOT is the java-localserver checkout root, so tests can read
#     fixture files (error pages, uploaded files) directly off disk to build
#     real assertions instead of hardcoding assumed text.

set -u

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

PASS=0
FAIL=0
SKIP=0
TMP_BODY="$(mktemp)"
TMP_HEADERS="$(mktemp)"
trap 'rm -f "$TMP_BODY" "$TMP_HEADERS"' EXIT

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() {
    echo -e "  ${GREEN}PASS${NC}: $1"
    PASS=$((PASS + 1))
}

fail() {
    echo -e "  ${RED}FAIL${NC}: $1"
    FAIL=$((FAIL + 1))
}

skip() {
    echo -e "  ${YELLOW}SKIP${NC}: $1"
    SKIP=$((SKIP + 1))
}

# run_test <name> <expected_status> <expected_body_substring|""> <curl args...>
run_test() {
    local name="$1"; shift
    local expected_status="$1"; shift
    local expected_substring="$1"; shift

    local actual_status
    actual_status="$(curl -s -o "$TMP_BODY" -D "$TMP_HEADERS" -m 15 -w "%{http_code}" "$@" 2>/dev/null)"

    if [ "$actual_status" != "$expected_status" ]; then
        fail "$name (expected status $expected_status, got $actual_status)"
        return 1
    fi

    if [ -n "$expected_substring" ] && ! grep -qF "$expected_substring" "$TMP_BODY"; then
        fail "$name (status OK, but body missing expected text: \"$expected_substring\")"
        echo "    --- actual body (first 5 lines) ---"
        sed 's/^/    /' "$TMP_BODY" | head -5
        echo "    -----------------------------------"
        return 1
    fi

    pass "$name"
    return 0
}

# run_test_header <name> <expected_status> <header_name_regex> <expected_value_substring> <curl args...>
run_test_header() {
    local name="$1"; shift
    local expected_status="$1"; shift
    local header_name="$1"; shift
    local expected_value="$1"; shift

    local actual_status
    actual_status="$(curl -s -o "$TMP_BODY" -D "$TMP_HEADERS" -m 15 -w "%{http_code}" "$@" 2>/dev/null)"

    if [ "$actual_status" != "$expected_status" ]; then
        fail "$name (expected status $expected_status, got $actual_status)"
        return 1
    fi

    if ! grep -qi "^${header_name}:.*${expected_value}" "$TMP_HEADERS"; then
        fail "$name (missing/incorrect header: $header_name)"
        echo "    --- actual headers ---"
        sed 's/^/    /' "$TMP_HEADERS"
        echo "    ----------------------"
        return 1
    fi

    pass "$name"
    return 0
}

# error_page_matches <status_code> -- reads the real error_pages/<code>.html off
# disk and checks the last response body matches it exactly. More robust than
# hardcoding assumed wording: it fails only if the server's actual custom
# error page changes, never because our guess at the text was wrong.
error_page_matches() {
    local code="$1"
    local expected_file="$PROJECT_ROOT/error_pages/${code}.html"
    if [ ! -f "$expected_file" ]; then
        echo "  (no local error_pages/${code}.html to compare against)"
        return 1
    fi
    diff -q "$expected_file" "$TMP_BODY" >/dev/null 2>&1
}

preflight() {
    echo "Target: $BASE_URL"
    if ! curl -s -o /dev/null -m 5 "$BASE_URL/"; then
        echo -e "${YELLOW}Server not reachable at $BASE_URL -- is it running (./run.sh)?${NC}"
        exit 1
    fi
    echo
}

summary() {
    echo "============================================"
    echo -e "  ${GREEN}$PASS passed${NC}, ${RED}$FAIL failed${NC}, ${YELLOW}$SKIP skipped${NC}"
    echo "============================================"
    if [ "$FAIL" -ne 0 ]; then
        exit 1
    fi
    exit 0
}
