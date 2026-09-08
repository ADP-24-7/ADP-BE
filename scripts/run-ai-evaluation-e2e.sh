#!/bin/sh
set -eu

BASE_URL="${ADP_BE_BASE_URL:-http://127.0.0.1:8080}"
RUNTIME_API_KEY="${ADP_RUNTIME_API_KEY:-local-dev-api-key}"
ADMIN_USER_ID="${ADP_ADMIN_USER_ID:-da-evaluation-reader}"
REAL_PROVIDER_CONFIRMED="${ADP_AI_E2E_CONFIRM_REAL_PROVIDER:-}"
RUN_ID="ai-eval-baseline-2026-09-07"
CASE_ID="customer-summary-ko-001"
RUN_SUFFIX="${AI_EVAL_RUN_SUFFIX:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
OUTPUT_ROOT="${AI_EVAL_OUTPUT_DIR:-build/ai-evaluation-e2e}"
OUTPUT_DIR="$OUTPUT_ROOT/$RUN_SUFFIX"
TMP_DIR=""

case "$BASE_URL" in
    http://127.0.0.1:*|http://localhost:*|http://\[::1\]:*) ;;
    *)
        printf '%s\n' "This local harness only accepts a loopback BE URL." >&2
        exit 2
        ;;
esac

if [ "$REAL_PROVIDER_CONFIRMED" != "YES" ]; then
    printf '%s\n' "Real provider execution requires ADP_AI_E2E_CONFIRM_REAL_PROVIDER=YES." >&2
    exit 2
fi

TMP_DIR="$(mktemp -d)"

cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT INT TERM

wait_for_be() {
    attempt=0
    until curl -fsS "$BASE_URL/actuator/health/readiness" >/dev/null 2>&1; do
        attempt=$((attempt + 1))
        if [ "$attempt" -ge 60 ]; then
            printf '%s\n' "BE readiness did not become available: $BASE_URL" >&2
            exit 1
        fi
        sleep 2
    done
}

submit() {
    profile_id="$1"
    destination_profile_id="$2"
    response_file="$TMP_DIR/$profile_id.json"
    request_suffix="$RUN_SUFFIX-$profile_id"

    if ! curl --fail-with-body -sS \
        -X POST "$BASE_URL/v1/runtime/executions" \
        -H "Content-Type: application/json" \
        -H "X-Request-Id: req-ai-eval-$request_suffix" \
        -H "X-Trace-Id: trace-ai-eval-$request_suffix" \
        -H "X-ADP-API-Key: $RUNTIME_API_KEY" \
        --data-binary "{
          \"institutionId\": \"institution_local\",
          \"approvalReference\": \"approval_ai_eval_$profile_id\",
          \"workloadId\": \"customer_summary\",
          \"purposeCode\": \"CUSTOMER_SUPPORT\",
          \"subjectScope\": \"customer:customer-100\",
          \"destinationProfileId\": \"$destination_profile_id\",
          \"idempotencyKey\": \"idem-ai-eval-$request_suffix\",
          \"evaluationRunId\": \"$RUN_ID\",
          \"evalCaseId\": \"$CASE_ID\",
          \"processingContexts\": [\"AI_USE\"],
          \"input\": {\"prompt\": \"승인된 고객 정보를 간단히 요약하세요\"}
        }" \
        >"$response_file"; then
        python3 -c '
import json
import sys

try:
    with open(sys.argv[1], encoding="utf-8") as source:
        error = json.load(source)
    reason = error.get("reasonCode")
    message = error.get("message")
    print(f"Runtime request failed: reason={reason} message={message}", file=sys.stderr)
except Exception:
    print("Runtime request failed with a non-JSON response", file=sys.stderr)
' "$response_file"
        exit 1
    fi

    python3 -c '
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    response = json.load(source)
execution_id = response.get("executionId")
if not execution_id:
    raise SystemExit("Runtime response has no executionId")
runtime_status = response.get("status")
connector_status = response.get("connectorStatus")
print(f"{sys.argv[2]}: execution={execution_id} runtime={runtime_status} connector={connector_status}")
' "$response_file" "$profile_id"
}

mkdir -p "$OUTPUT_DIR"
wait_for_be

submit "nvidia-nemotron-3.5-lightning-30b-a3b" "dest_nvidia-nemotron-3-5-lightning-30b-a3b"
submit "meta-muse-glimmer-30b" "dest_meta-muse-glimmer-30b"
submit "google-gemma-4-31b-it" "dest_google-gemma-4-31b-it"

curl -fsS \
    -H "X-ADP-User-Id: $ADMIN_USER_ID" \
    -H "X-ADP-User-Roles: PRIVILEGED_OPERATOR" \
    "$BASE_URL/api/admin/ai/evaluation-runs/$RUN_ID/readiness" \
    >"$OUTPUT_DIR/readiness.json"

python3 scripts/validate_ai_evaluation_e2e.py readiness \
    --submission "nvidia-nemotron-3.5-lightning-30b-a3b=$TMP_DIR/nvidia-nemotron-3.5-lightning-30b-a3b.json" \
    --submission "meta-muse-glimmer-30b=$TMP_DIR/meta-muse-glimmer-30b.json" \
    --submission "google-gemma-4-31b-it=$TMP_DIR/google-gemma-4-31b-it.json" \
    --readiness "$OUTPUT_DIR/readiness.json"

curl -fsS \
    -H "X-ADP-User-Id: $ADMIN_USER_ID" \
    -H "X-ADP-User-Roles: PRIVILEGED_OPERATOR" \
    "$BASE_URL/api/admin/ai/evaluation-runs/$RUN_ID/bundle" \
    >"$OUTPUT_DIR/bundle.json"

python3 scripts/validate_ai_evaluation_e2e.py bundle \
    --submission "nvidia-nemotron-3.5-lightning-30b-a3b=$TMP_DIR/nvidia-nemotron-3.5-lightning-30b-a3b.json" \
    --submission "meta-muse-glimmer-30b=$TMP_DIR/meta-muse-glimmer-30b.json" \
    --submission "google-gemma-4-31b-it=$TMP_DIR/google-gemma-4-31b-it.json" \
    --bundle "$OUTPUT_DIR/bundle.json" \
    --evaluation-run-id "$RUN_ID"

printf '%s\n' "Bundle exported: $OUTPUT_DIR/bundle.json"
