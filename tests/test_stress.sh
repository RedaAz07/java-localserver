#!/usr/bin/env bash
# Tests.md #36-37: Memory Leak Test, Stress Test
# Runs a SHORT siege burst by default so this is safe to include in a normal
# run_all.sh pass. For the real thresholds from Tests.md (60s/2min runs,
# 99.5% availability target), run siege directly and manually per Tests.md.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
preflight

echo "# 36-37. Memory Leak / Stress Test"

if ! command -v siege >/dev/null 2>&1; then
    skip "siege not installed -- install it and see Tests.md #36-37 for the full manual commands"
    echo
    summary
fi

DURATION="${SIEGE_DURATION:-10S}"
CONCURRENCY="${SIEGE_CONCURRENCY:-10}"

echo "  Running: siege -b -c${CONCURRENCY} -t${DURATION} $BASE_URL/index.html"
echo "  (this is a quick smoke check -- run the full Tests.md commands manually for real numbers)"

OUTPUT="$(siege -b -c"$CONCURRENCY" -t"$DURATION" "$BASE_URL/index.html" 2>&1)"
AVAILABILITY="$(echo "$OUTPUT" | grep -i "Availability" | grep -oE '[0-9]+\.[0-9]+' | head -1)"

echo "$OUTPUT" | grep -iE "availability|transactions|failed"

if [ -z "$AVAILABILITY" ]; then
    fail "couldn't parse availability from siege output"
else
    # bc may not be installed everywhere -- fall back to a plain string compare.
    if command -v bc >/dev/null 2>&1 && [ "$(echo "$AVAILABILITY >= 99.5" | bc)" = "1" ]; then
        pass "availability $AVAILABILITY% meets the 99.5% target"
    elif command -v bc >/dev/null 2>&1; then
        fail "availability $AVAILABILITY% is below the 99.5% target"
    else
        echo "  (availability: $AVAILABILITY% -- install bc for an automatic pass/fail here)"
        skip "availability threshold check (bc not installed)"
    fi
fi

# Confirm the server survived and is still responding after the burst.
run_test "server still responds after the stress burst" 200 "" "$BASE_URL/index.html"

echo
summary
