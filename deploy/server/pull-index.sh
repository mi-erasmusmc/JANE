#!/usr/bin/env bash
# Pull the promoted index from S3, extract, atomic symlink flip, trigger reload.
# See MODERNIZATION_PLAN.md section 5. Idempotent: no-op if already on the promoted version.
set -euo pipefail

BUCKET="${JANE_S3_BUCKET:?set JANE_S3_BUCKET}"
BASE="${JANE_BASE:-/var/jane}"
PORT="${JANE_PORT:-8000}"
ADMIN_KEY="${JANE_ADMIN_API_KEY:-}"

mkdir -p "${BASE}/staging"
VERSION="$(aws s3 cp "s3://${BUCKET}/current.txt" - | tr -d '[:space:]')"
if [[ -z "${VERSION}" ]]; then
  echo "No current.txt pointer found; nothing to do." >&2
  exit 0
fi

# Already serving this version? (symlink target basename matches)
if [[ -L "${BASE}/current" && "$(basename "$(readlink -f "${BASE}/current")")" == "${VERSION}" ]]; then
  echo "Already on ${VERSION}; nothing to do."
  exit 0
fi

ARCHIVE="${BASE}/staging/${VERSION}.tar.zst"
DEST="${BASE}/${VERSION}"

# 1. Download + 2. extract (only if not already extracted).
if [[ ! -d "${DEST}" ]]; then
  aws s3 cp "s3://${BUCKET}/index-${VERSION}.tar.zst" "${ARCHIVE}"
  mkdir -p "${DEST}.incoming"
  tar --use-compress-program=unzstd -xf "${ARCHIVE}" -C "${DEST}.incoming"
  # The tarball contains the index dir contents at top level (see make_tarball).
  mv -T "${DEST}.incoming" "${DEST}"
  rm -f "${ARCHIVE}"
fi

# 3. Atomic symlink flip.
ln -sfn "${DEST}" "${BASE}/current.new"
mv -T "${BASE}/current.new" "${BASE}/current"

# 4. Trigger reload in all workers.
curl -fsS -H "x-api-key: ${ADMIN_KEY}" -X POST "http://127.0.0.1:${PORT}/admin/reload" >/dev/null
echo "Promoted and reloaded ${VERSION}."

# 5. Prune all but the last 2 generations.
ls -1dt "${BASE}"/index-* 2>/dev/null | tail -n +3 | while read -r old; do
  [[ "$(readlink -f "${BASE}/current")" == "$(readlink -f "${old}")" ]] && continue
  rm -rf "${old}"
done
