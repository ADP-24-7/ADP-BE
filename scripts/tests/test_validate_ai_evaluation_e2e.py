from __future__ import annotations

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from scripts.validate_ai_evaluation_e2e import (
    ValidationError,
    load_submissions,
    validate_bundle,
    validate_readiness,
)


class AiEvaluationE2EValidatorTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        root = Path(self.temp_dir.name)
        self.submission_args: list[str] = []
        self.submitted: dict[str, str] = {}
        for index, profile in enumerate(("model-a", "model-b", "model-c"), start=1):
            execution_id = f"exec-{index}"
            path = root / f"{profile}.json"
            path.write_text(json.dumps({"executionId": execution_id}), encoding="utf-8")
            self.submission_args.append(f"{profile}={path}")
            self.submitted[profile] = execution_id

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def readiness(self) -> dict:
        return {
            "status": "READY",
            "bundle_available": True,
            "expected_execution_count": 3,
            "observed_execution_count": 3,
            "complete_evidence_count": 3,
            "missing_execution_count": 0,
            "unexpected_execution_count": 0,
            "case_models": [
                {"profile_id": profile, "execution_id": execution_id}
                for profile, execution_id in self.submitted.items()
            ],
        }

    def bundle(self) -> dict:
        rows = [
            {"model_profile_id": profile, "execution_id": execution_id}
            for profile, execution_id in self.submitted.items()
        ]
        return {
            "manifest": {
                "evaluation_run_id": "run-1",
                "execution_count": 3,
                "model_count": 3,
                "content_digest": "sha256:" + "a" * 64,
            },
            "case_results": rows,
            "runtime_metrics": [dict(row) for row in rows],
            "trace_index": [
                {"execution_id": execution_id} for execution_id in self.submitted.values()
            ],
        }

    def test_accepts_exact_fresh_execution_binding(self) -> None:
        submitted = load_submissions(self.submission_args)
        validate_readiness(submitted, self.readiness())
        validate_bundle(submitted, self.bundle(), "run-1")

    def test_rejects_stale_readiness_execution(self) -> None:
        readiness = self.readiness()
        readiness["case_models"][1]["execution_id"] = "exec-yesterday"
        with self.assertRaisesRegex(ValidationError, "freshly submitted"):
            validate_readiness(self.submitted, readiness)

    def test_rejects_stale_execution_in_every_bundle_section(self) -> None:
        for section in ("case_results", "runtime_metrics", "trace_index"):
            with self.subTest(section=section):
                bundle = self.bundle()
                bundle[section][1]["execution_id"] = "exec-yesterday"
                with self.assertRaisesRegex(ValidationError, "freshly submitted"):
                    validate_bundle(self.submitted, bundle, "run-1")

    def test_harness_requires_explicit_real_provider_confirmation(self) -> None:
        repository = Path(__file__).resolve().parents[2]
        environment = os.environ.copy()
        environment.pop("ADP_AI_E2E_CONFIRM_REAL_PROVIDER", None)
        result = subprocess.run(
            ["sh", "scripts/run-ai-evaluation-e2e.sh"],
            cwd=repository,
            env=environment,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(result.returncode, 2)
        self.assertIn("ADP_AI_E2E_CONFIRM_REAL_PROVIDER=YES", result.stderr)


if __name__ == "__main__":
    unittest.main()
