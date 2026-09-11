#!/bin/sh
set -eu

PROFILE="${INTEGRATION_PROFILE:-demo}"
BE_PORT="${ADP_BE_PORT:-8080}"
FE_PORT="${ADP_FE_PORT:-5173}"
DA_PORT="${ADP_DA_PORT:-8010}"
DOCS_PORT="${ADP_DOCS_PORT:-3000}"

curl -fsS "http://localhost:${BE_PORT}/actuator/health/readiness" >/dev/null
curl -fsS "http://localhost:${FE_PORT}" >/dev/null
curl -fsS "http://localhost:${DA_PORT}/health" >/dev/null
curl -fsS "http://localhost:${DOCS_PORT}" >/dev/null

info="$(curl -fsS "http://localhost:${BE_PORT}/api/internal/info")"
python3 - "$PROFILE" "$info" <<'PY'
import json
import sys

expected, raw = sys.argv[1], sys.argv[2]
info = json.loads(raw)
if info.get("runtimeProfile") != expected:
    raise SystemExit(f"runtime profile mismatch: expected={expected}, actual={info.get('runtimeProfile')}")
if expected == "demo" and info.get("dataProvenance") != "SYNTHETIC":
    raise SystemExit("demo profile must report SYNTHETIC data provenance")
PY

printf '%s\n' "Local integration verified: profile=${PROFILE}, BE/FE/DA/Docs/PostgreSQL healthy"
