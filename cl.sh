#!/usr/bin/env bash
export ANTHROPIC_BASE_URL="https://api.deepseek.com/anthropic"
export ANTHROPIC_AUTH_TOKEN="sk-133cebd685d6466ca4d6bafe566b644f"
export ANTHROPIC_MODEL="deepseek-v4-pro[1m]"
exec claude --model deepseek-v4-pro[1m] "$@"