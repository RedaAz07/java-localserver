#!/usr/bin/env bash
# Tests.md #1: Basic Connection
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
preflight

echo "# 1. Basic Connection"
run_test "server responds on base URL" 200 "" "$BASE_URL/"
echo

summary
