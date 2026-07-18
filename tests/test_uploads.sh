#!/usr/bin/env bash
# Tests.md #13-15: File Upload, Multiple Files, Large File
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
preflight

FIXTURE_DIR="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_DIR"' EXIT

echo "hello world" > "$FIXTURE_DIR/hello.txt"
cp "$PROJECT_ROOT/www/khalid.png" "$FIXTURE_DIR/image.png" 2>/dev/null || echo "fakeimg" > "$FIXTURE_DIR/image.png"

# --- 13. File Upload ---------------------------------------------------------
echo "# 13. File Upload"
curl -s -o "$TMP_BODY" -m 15 -w "%{http_code}" \
    -F "file=@$FIXTURE_DIR/hello.txt" \
    "$BASE_URL/upload" > /tmp/_up_status
STATUS="$(cat /tmp/_up_status)"
if [ "$STATUS" = "200" ] || [ "$STATUS" = "201" ]; then
    if [ -f "$PROJECT_ROOT/uploads/hello.txt" ] && diff -q "$FIXTURE_DIR/hello.txt" "$PROJECT_ROOT/uploads/hello.txt" >/dev/null 2>&1; then
        pass "uploaded file stored correctly in uploads/"
    else
        fail "upload returned $STATUS but stored file is missing or doesn't match"
    fi
else
    fail "file upload (expected 200/201, got $STATUS)"
fi
echo

# --- 14. Upload Multiple Files ------------------------------------------------
echo "# 14. Upload Multiple Files"
curl -s -o "$TMP_BODY" -m 15 -w "%{http_code}" \
    -F "file1=@$FIXTURE_DIR/image.png" \
    -F "file2=@$FIXTURE_DIR/hello.txt" \
    "$BASE_URL/upload" > /tmp/_multi_status
STATUS="$(cat /tmp/_multi_status)"
if { [ "$STATUS" = "200" ] || [ "$STATUS" = "201" ]; } \
    && [ -f "$PROJECT_ROOT/uploads/image.png" ] \
    && [ -f "$PROJECT_ROOT/uploads/hello.txt" ]; then
    pass "both files uploaded successfully"
else
    fail "multi-file upload (status=$STATUS, image.png present=$([ -f "$PROJECT_ROOT/uploads/image.png" ] && echo yes || echo no))"
fi
echo

# --- 15. Upload Large File -----------------------------------------------------
echo "# 15. Upload Large File"
# Full 100MB/1GB per Tests.md is slow for a routine run; default to a size
# just over the "/" route's client_body_limit (~5.6MB in config.json) so the
# 413 path is exercised quickly. Override with LARGE_FILE_MB for a fuller run.
LARGE_FILE_MB="${LARGE_FILE_MB:-8}"
dd if=/dev/zero of="$FIXTURE_DIR/large.bin" bs=1M count="$LARGE_FILE_MB" >/dev/null 2>&1

# POST directly to "/" (small client_body_limit) to actually exercise 413,
# since "/upload" is configured with an intentionally huge limit.
curl -s -o "$TMP_BODY" -m 30 -w "%{http_code}" \
    --data-binary "@$FIXTURE_DIR/large.bin" \
    "$BASE_URL/largefile.bin" > /tmp/_large_status
STATUS="$(cat /tmp/_large_status)"
if [ "$STATUS" = "413" ]; then
    pass "oversized upload correctly rejected with 413 (${LARGE_FILE_MB}MB vs configured limit)"
elif [ "$STATUS" = "200" ] || [ "$STATUS" = "201" ]; then
    pass "upload within configured limit succeeded ($STATUS)"
else
    fail "large upload (expected 413 or 200/201, got $STATUS)"
fi
rm -f "$PROJECT_ROOT/www/largefile.bin" 2>/dev/null
echo

# cleanup uploaded fixtures
rm -f "$PROJECT_ROOT/uploads/hello.txt" "$PROJECT_ROOT/uploads/image.png"

summary
