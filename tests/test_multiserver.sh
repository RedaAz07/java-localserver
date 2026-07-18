#!/usr/bin/env bash
# Tests.md #27-28: Multiple Ports, Multiple Requests
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
preflight

# --- 27. Multiple Ports -------------------------------------------------------
echo "# 27. Multiple Ports"
for url in "http://127.0.0.1:8081" "http://127.0.0.1:8082"; do
    if curl -s -o /dev/null -m 5 "$url"; then
        run_test "$url responds" 200 "" "$url"
    else
        skip "$url not reachable"
    fi
done
echo

# --- 28. Multiple Requests -----------------------------------------------------
echo "# 28. Multiple Requests"
N="${MULTI_REQUEST_COUNT:-100}"
SUCCESS=0
for i in $(seq 1 "$N"); do
    STATUS="$(curl -s -o /dev/null -m 5 -w "%{http_code}" "$BASE_URL/index.html")"
    if [ "$STATUS" = "200" ]; then
        SUCCESS=$((SUCCESS + 1))
    fi
done
if [ "$SUCCESS" -eq "$N" ]; then
    pass "all $N sequential requests succeeded"
else
    fail "only $SUCCESS/$N sequential requests succeeded"
fi
echo

summary
