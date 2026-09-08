#!/bin/sh
set -eu

NCP_ENV="${NCP_ENV:-.env}"
REFERENCE_FILE="${ADP_NCP_INGEST_REFERENCE_FILE:-../ADP-DA/03_digital_asset/artifacts/be_loader_v1/ncp-ingest-reference.json}"
GRADLE_IMAGE="${GRADLE_IMAGE:-gradle:8.14.3-jdk21}"
EVIDENCE_OUTPUT="${ADP_NCP_INGEST_EVIDENCE_OUTPUT:-build/ncp-artifact-ingest-e2e/evidence.json}"

if [ ! -f "$NCP_ENV" ]; then
    printf '%s\n' "NCP environment file not found: $NCP_ENV" >&2
    exit 2
fi

set -a
# shellcheck disable=SC1090
. "$NCP_ENV"
set +a

if [ "${ADP_NCP_ARTIFACT_INGEST_CONFIRM:-NO}" != "YES" ]; then
    printf '%s\n' "Real NCP ingest requires ADP_NCP_ARTIFACT_INGEST_CONFIRM=YES." >&2
    exit 2
fi
if [ -z "${NCLOUD_ACCESS_KEY:-}" ] || [ -z "${NCLOUD_SECRET_KEY:-}" ]; then
    printf '%s\n' "NCLOUD_ACCESS_KEY and NCLOUD_SECRET_KEY are required." >&2
    exit 2
fi
if [ ! -f "$REFERENCE_FILE" ]; then
    printf '%s\n' "DA NCP ingest reference not found: $REFERENCE_FILE" >&2
    exit 2
fi

MANIFEST_REFERENCE="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["manifestReference"])' "$REFERENCE_FILE")"
EXPECTED_CONTENT_DIGEST="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["expectedContentDigest"])' "$REFERENCE_FILE")"

cleanup() {
    docker compose rm -sf postgres-test >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker compose rm -sf postgres-test >/dev/null
docker compose up -d postgres-test

docker run --rm --network adp-local \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-test:5432/adp \
    -e SPRING_DATASOURCE_USERNAME=adp \
    -e SPRING_DATASOURCE_PASSWORD=adp \
    -e SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2 \
    -e SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0 \
    -e NCLOUD_ACCESS_KEY \
    -e NCLOUD_SECRET_KEY \
    -e NCLOUD_REGION="${NCLOUD_REGION:-KR}" \
    -e ADP_NCP_OBJECT_STORAGE_ENDPOINT="${ADP_NCP_OBJECT_STORAGE_ENDPOINT:-https://kr.object.ncloudstorage.com}" \
    -e ADP_NCP_ARTIFACT_BUCKET="${ADP_NCP_ARTIFACT_BUCKET:-adp-qa-data-artifacts}" \
    -e ADP_NCP_ARTIFACT_INGEST_CONFIRM=YES \
    -e ADP_NCP_MANIFEST_REFERENCE="$MANIFEST_REFERENCE" \
    -e ADP_NCP_EXPECTED_CONTENT_DIGEST="$EXPECTED_CONTENT_DIGEST" \
    -v "$(pwd)":/workspace \
    -v adp-be-gradle-cache:/home/gradle/.gradle \
    -w /workspace \
    "$GRADLE_IMAGE" gradle --no-daemon \
    --project-cache-dir /home/gradle/.gradle/ncp-e2e-project-cache \
    test --tests '*NcpDigitalAssetArtifactIngestionE2ETests'

BE_GIT_SHA="$(git rev-parse HEAD)"
DA_GIT_SHA="$(git -C ../ADP-DA rev-parse HEAD)"
if git diff --quiet && git diff --cached --quiet \
    && [ -z "$(git ls-files --others --exclude-standard)" ]; then
    BE_WORKTREE_CLEAN="true"
else
    BE_WORKTREE_CLEAN="false"
fi
mkdir -p "$(dirname "$EVIDENCE_OUTPUT")"
python3 - "$EVIDENCE_OUTPUT" "$BE_GIT_SHA" "$BE_WORKTREE_CLEAN" "$DA_GIT_SHA" \
    "$MANIFEST_REFERENCE" "$EXPECTED_CONTENT_DIGEST" <<'PY'
import datetime
import json
import sys
from pathlib import Path

output, be_sha, be_worktree_clean, da_sha, reference, digest = sys.argv[1:]
evidence = {
    "schema_version": "adp-ncp-artifact-ingest-e2e-evidence/v1",
    "run_type": "NCP_BE_DIGITAL_ASSET_BUNDLE_INGEST",
    "status": "PASS",
    "executed_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "be_git_sha": be_sha,
    "be_worktree_clean": be_worktree_clean == "true",
    "da_git_sha": da_sha,
    "endpoint": "https://kr.object.ncloudstorage.com",
    "bucket": "adp-qa-data-artifacts",
    "manifest_reference": reference,
    "expected_content_digest": digest,
    "lifecycle_stage": "CANDIDATE",
    "credential_values_recorded": False,
}
Path(output).write_text(
    json.dumps(evidence, ensure_ascii=False, indent=2) + "\n",
    encoding="utf-8",
)
PY

printf '%s\n' "NCP Bundle ingest E2E passed without recording credential values."
printf '%s\n' "Evidence: $EVIDENCE_OUTPUT"
