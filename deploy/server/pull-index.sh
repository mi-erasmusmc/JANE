#!/usr/bin/env bash
# Pull the promoted index from S3, extract, atomic symlink flip, trigger reload.
# See MODERNIZATION_PLAN.md section 5. TODO: fill in bucket + endpoint; verify before flip.
set -euo pipefail

BUCKET="${JANE_S3_BUCKET:?set JANE_S3_BUCKET}"
BASE="/var/jane"
VERSION="$(aws s3 cp "s3://${BUCKET}/current.txt" -)"

# TODO:
#   1. aws s3 cp "s3://${BUCKET}/${VERSION}.tar.zst" "${BASE}/staging/"
#   2. extract to "${BASE}/${VERSION}/"
#   3. ln -sfn "${BASE}/${VERSION}" "${BASE}/current.new" && mv -T "${BASE}/current.new" "${BASE}/current"
#   4. curl -fsS -H "x-api-key: ${JANE_ADMIN_API_KEY}" -X POST http://127.0.0.1:8000/admin/reload
#   5. prune all but the last 1-2 generations
echo "promoted version: ${VERSION} (script is a scaffold)"
