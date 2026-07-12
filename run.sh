#!/usr/bin/env bash
set -euo pipefail

mkdir -p out
# Find and compile all .java files (handles paths with spaces)
if ! find . -type f -name '*.java' -print0 | xargs -0 javac -d out; then
	echo "Compilation failed"
	exit 1
fi

java -cp out Main
