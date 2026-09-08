#!/usr/bin/env python3
"""Validate that an AI evaluation export contains this harness run's executions."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


class ValidationError(RuntimeError):
    pass


def load_object(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValidationError(f"Invalid JSON file: {path}") from error
    if not isinstance(value, dict):
        raise ValidationError(f"JSON root must be an object: {path}")
    return value


def load_submissions(values: list[str]) -> dict[str, str]:
    submitted: dict[str, str] = {}
    for value in values:
        profile_id, separator, path_text = value.partition("=")
        if not separator or not profile_id or not path_text:
            raise ValidationError("Submission must use PROFILE_ID=RESPONSE_PATH")
        if profile_id in submitted:
            raise ValidationError(f"Duplicate submitted profile: {profile_id}")
        execution_id = load_object(Path(path_text)).get("executionId")
        if not isinstance(execution_id, str) or not execution_id:
            raise ValidationError(f"Runtime response has no executionId: {profile_id}")
        if execution_id in submitted.values():
            raise ValidationError(f"Duplicate submitted execution ID: {execution_id}")
        submitted[profile_id] = execution_id
    if not submitted:
        raise ValidationError("At least one submission is required")
    return submitted


def profile_execution_map(
    rows: Any,
    *,
    profile_field: str,
    section: str,
) -> dict[str, str]:
    if not isinstance(rows, list):
        raise ValidationError(f"{section} must be an array")
    result: dict[str, str] = {}
    for row in rows:
        if not isinstance(row, dict):
            raise ValidationError(f"{section} entries must be objects")
        profile_id = row.get(profile_field)
        execution_id = row.get("execution_id")
        if not isinstance(profile_id, str) or not isinstance(execution_id, str):
            raise ValidationError(f"{section} entry has invalid profile/execution identity")
        if profile_id in result:
            raise ValidationError(f"{section} contains duplicate profile: {profile_id}")
        result[profile_id] = execution_id
    return result


def require_exact_mapping(
    submitted: dict[str, str],
    actual: dict[str, str],
    section: str,
) -> None:
    if actual != submitted:
        raise ValidationError(
            f"{section} does not contain the freshly submitted profile/execution mapping"
        )


def validate_readiness(submitted: dict[str, str], readiness: dict[str, Any]) -> None:
    expected_count = len(submitted)
    if readiness.get("status") != "READY" or readiness.get("bundle_available") is not True:
        raise ValidationError("Evaluation Run is not ready for Bundle export")
    required_counts = {
        "expected_execution_count": expected_count,
        "observed_execution_count": expected_count,
        "complete_evidence_count": expected_count,
        "missing_execution_count": 0,
        "unexpected_execution_count": 0,
    }
    for field, expected in required_counts.items():
        if readiness.get(field) != expected:
            raise ValidationError(f"Readiness field mismatch: {field}")
    actual = profile_execution_map(
        readiness.get("case_models"), profile_field="profile_id", section="readiness.case_models"
    )
    require_exact_mapping(submitted, actual, "readiness.case_models")


def validate_bundle(
    submitted: dict[str, str],
    bundle: dict[str, Any],
    evaluation_run_id: str,
) -> None:
    manifest = bundle.get("manifest")
    if not isinstance(manifest, dict):
        raise ValidationError("Bundle manifest is missing")
    if manifest.get("evaluation_run_id") != evaluation_run_id:
        raise ValidationError("Exported Bundle Run ID mismatch")
    if manifest.get("execution_count") != len(submitted):
        raise ValidationError("Exported Bundle execution count mismatch")
    if manifest.get("model_count") != len(submitted):
        raise ValidationError("Exported Bundle model count mismatch")
    digest = manifest.get("content_digest")
    if not isinstance(digest, str) or not digest.startswith("sha256:"):
        raise ValidationError("Exported Bundle has no canonical content digest")

    for section in ("case_results", "runtime_metrics"):
        actual = profile_execution_map(
            bundle.get(section), profile_field="model_profile_id", section=section
        )
        require_exact_mapping(submitted, actual, section)

    trace_index = bundle.get("trace_index")
    if not isinstance(trace_index, list):
        raise ValidationError("trace_index must be an array")
    trace_ids = []
    for row in trace_index:
        if not isinstance(row, dict) or not isinstance(row.get("execution_id"), str):
            raise ValidationError("trace_index entry has invalid execution identity")
        trace_ids.append(row["execution_id"])
    if len(trace_ids) != len(set(trace_ids)) or set(trace_ids) != set(submitted.values()):
        raise ValidationError("trace_index does not contain the freshly submitted executions")


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    subparsers = root.add_subparsers(dest="command", required=True)
    readiness = subparsers.add_parser("readiness")
    readiness.add_argument("--submission", action="append", required=True)
    readiness.add_argument("--readiness", type=Path, required=True)
    bundle = subparsers.add_parser("bundle")
    bundle.add_argument("--submission", action="append", required=True)
    bundle.add_argument("--bundle", type=Path, required=True)
    bundle.add_argument("--evaluation-run-id", required=True)
    return root


def main() -> None:
    args = parser().parse_args()
    try:
        submitted = load_submissions(args.submission)
        if args.command == "readiness":
            document = load_object(args.readiness)
            validate_readiness(submitted, document)
            print(f"Readiness is bound to {len(submitted)} freshly submitted executions")
        else:
            document = load_object(args.bundle)
            validate_bundle(submitted, document, args.evaluation_run_id)
            print(f"Bundle is bound to {len(submitted)} freshly submitted executions")
    except ValidationError as error:
        raise SystemExit(str(error)) from error


if __name__ == "__main__":
    main()
