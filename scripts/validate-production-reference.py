#!/usr/bin/env python3
"""Validate the production reference contract without claiming unverified cloud operation."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


SCHEMA_VERSION = "adp-production-reference/v1"
STATUSES = {
    "IMPLEMENTED_LOCAL",
    "VERIFIED_QA_FOUNDATION",
    "DESIGN_ONLY",
    "OPTIONAL_CLOUD_UNVERIFIED",
}
OWNERS = {"BE", "INFRA", "BE_INFRA"}
REQUIRED_COMPONENTS = {
    "central-api-gateway",
    "admin-oidc",
    "service-mtls",
    "gateway-runtime",
    "egress-network-control",
    "provider-adapters",
    "postgresql-state",
    "postgresql-ha",
    "object-storage-handoff",
    "secret-manager-kms",
    "prometheus-alerting",
    "siem-integration",
    "backup-restore-dr",
    "deployment-rollback",
}
REQUIRED_FALSE_CLAIMS = {
    "productionCloudRuntimeVerified",
    "highAvailabilityVerified",
    "disasterRecoveryVerified",
    "publicDemoVerified",
}
PRODUCTION_LIKE_VALUES = {
    "ADP_RUNTIME_PROFILE": "production-like",
    "ADP_DATA_PROVENANCE": "NONE",
    "ADP_LOCAL_USER_AUTH_ENABLED": "false",
    "ADP_LOCAL_FIXTURES_ENABLED": "false",
    "ADP_MOCK_RUNTIME_ENABLED": "false",
    "ADP_AI_CONNECTOR_ENABLED": "false",
    "ADP_DATA_ACCESS_PREVIEW_ENABLED": "false",
    "ADP_CONTEXT_PREVIEW_ENABLED": "false",
    "ADP_REQUEST_FRESHNESS_ENABLED": "true",
    "ADP_ACCEPT_CALLER_TRACE_ID": "false",
    "ADP_EGRESS_ALLOW_PRIVATE_DESTINATIONS": "false",
    "ADP_PROMETHEUS_PUBLIC": "false",
    "VITE_API_MODE": "real",
    "VITE_LOCAL_BFF_ENABLED": "false",
    "VITE_RUNTIME_PROFILE": "production-like",
    "VITE_DATA_PROVENANCE": "NONE",
}


def read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            key, value = stripped.split("=", 1)
            values[key] = value
    return values


def validate(root: Path, contract: dict, profile: dict[str, str]) -> list[str]:
    errors: list[str] = []
    if contract.get("schemaVersion") != SCHEMA_VERSION:
        errors.append("unsupported production reference schema")

    claims = contract.get("claims", {})
    for claim in REQUIRED_FALSE_CLAIMS:
        if claims.get(claim) is not False:
            errors.append(f"unverified production claim must remain false: {claim}")
    if claims.get("localProductRuntimeVerified") is not True:
        errors.append("local product runtime evidence must remain explicit")

    components = contract.get("components", [])
    component_ids = [component.get("id") for component in components]
    if len(component_ids) != len(set(component_ids)):
        errors.append("production reference component ids must be unique")
    missing = sorted(REQUIRED_COMPONENTS - set(component_ids))
    if missing:
        errors.append(f"required production reference components are missing: {', '.join(missing)}")

    for component in components:
        component_id = component.get("id", "<missing>")
        if component.get("status") not in STATUSES:
            errors.append(f"{component_id}: invalid implementation status")
        if component.get("owner") not in OWNERS:
            errors.append(f"{component_id}: invalid owner")
        evidence = component.get("evidence", [])
        if not evidence:
            errors.append(f"{component_id}: evidence reference is required")
        for relative_path in evidence:
            if not (root / relative_path).exists():
                errors.append(f"{component_id}: evidence path is missing: {relative_path}")

    for key, expected in PRODUCTION_LIKE_VALUES.items():
        if profile.get(key) != expected:
            errors.append(
                f"production-like profile mismatch: {key} expected={expected} actual={profile.get(key)}"
            )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--contract",
        type=Path,
        default=Path("config/architecture/production-reference.json"),
    )
    parser.add_argument(
        "--profile",
        type=Path,
        default=Path("config/integration/profiles/production-like.env"),
    )
    args = parser.parse_args()
    contract_path = args.contract.resolve()
    root = contract_path.parents[2]
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    errors = validate(root, contract, read_env(args.profile.resolve()))
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("Production reference contract valid; cloud runtime claims remain unverified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
