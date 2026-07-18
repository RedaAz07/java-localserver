#!/usr/bin/env bash
#
# Runs every tests/test_*.sh in sequence and prints an overall summary.
# Individual scripts still print their own detailed PASS/FAIL/SKIP output --
# this just aggregates the final pass/fail verdict across all of them.
#
# Usage:
#   ./tests/run_all.sh
#   BASE_URL=http://127.0.0.1:8081 ./tests/run_all.sh
#   RUN_SLOW_TESTS=1 ./tests/run_all.sh    # include the connection-timeout test
#
# See tests/MANUAL_TESTS.md for the two Tests.md sections (#34 config,
# #38 browser) that can't be automated this way.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SCRIPTS=(
    test_basic.sh
    test_static_files.sh
    test_methods.sh
    test_uploads.sh
    test_cookies_sessions.sh
    test_routing.sh
    test_protocol.sh
    test_multiserver.sh
    test_error_pages.sh
    test_security.sh
    test_cgi.sh
    test_stress.sh
)

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

TOTAL_OK=0
TOTAL_FAILED=0
FAILED_NAMES=()

for script in "${SCRIPTS[@]}"; do
    path="$SCRIPT_DIR/$script"
    if [ ! -f "$path" ]; then
        echo -e "${YELLOW}(skipping $script -- file not found)${NC}"
        continue
    fi

    echo -e "${BOLD}=========================================="
    echo -e " $script"
    echo -e "==========================================${NC}"

    bash "$path"
    status=$?

    if [ "$status" -eq 0 ]; then
        TOTAL_OK=$((TOTAL_OK + 1))
    else
        TOTAL_FAILED=$((TOTAL_FAILED + 1))
        FAILED_NAMES+=("$script")
    fi
    echo
done

echo -e "${BOLD}=========================================="
echo -e " Overall: ${GREEN}${TOTAL_OK} suites passed${NC}, ${RED}${TOTAL_FAILED} suites failed${NC}"
if [ "${#FAILED_NAMES[@]}" -gt 0 ]; then
    echo -e " Failed suites: ${RED}${FAILED_NAMES[*]}${NC}"
fi
echo -e " See tests/MANUAL_TESTS.md for #34 (config) and #38 (browser)"
echo -e "==========================================${NC}"

[ "$TOTAL_FAILED" -eq 0 ]
