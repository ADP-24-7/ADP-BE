import copy
import importlib.util
import json
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "validate-production-reference.py"
SPEC = importlib.util.spec_from_file_location("validate_production_reference", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class ProductionReferenceValidatorTests(unittest.TestCase):
    def setUp(self):
        self.root = SCRIPT.parents[1]
        self.contract = json.loads(
            (self.root / "config/architecture/production-reference.json").read_text(encoding="utf-8")
        )
        self.profile = MODULE.read_env(
            self.root / "config/integration/profiles/production-like.env"
        )

    def test_accepts_versioned_reference_and_fail_closed_profile(self):
        self.assertEqual([], MODULE.validate(self.root, self.contract, self.profile))

    def test_rejects_unverified_cloud_claim(self):
        contract = copy.deepcopy(self.contract)
        contract["claims"]["productionCloudRuntimeVerified"] = True

        errors = MODULE.validate(self.root, contract, self.profile)

        self.assertIn(
            "architecture claim mismatch: productionCloudRuntimeVerified expected=False actual=True",
            errors,
        )

    def test_rejects_qa_foundation_claim_downgrade(self):
        contract = copy.deepcopy(self.contract)
        contract["claims"]["ncpQaFoundationVerified"] = False

        errors = MODULE.validate(self.root, contract, self.profile)

        self.assertIn(
            "architecture claim mismatch: ncpQaFoundationVerified expected=True actual=False",
            errors,
        )

    def test_rejects_component_status_promotion_without_validator_change(self):
        contract = copy.deepcopy(self.contract)
        component = next(
            item for item in contract["components"] if item["id"] == "postgresql-ha"
        )
        component["status"] = "VERIFIED_QA_FOUNDATION"

        errors = MODULE.validate(self.root, contract, self.profile)

        self.assertIn(
            "postgresql-ha: status mismatch expected=DESIGN_ONLY actual=VERIFIED_QA_FOUNDATION",
            errors,
        )

    def test_rejects_missing_architecture_component(self):
        contract = copy.deepcopy(self.contract)
        contract["components"] = [
            component
            for component in contract["components"]
            if component["id"] != "secret-manager-kms"
        ]

        errors = MODULE.validate(self.root, contract, self.profile)

        self.assertIn(
            "required production reference components are missing: secret-manager-kms",
            errors,
        )

    def test_rejects_unsafe_production_like_override(self):
        profile = dict(self.profile)
        profile["ADP_LOCAL_FIXTURES_ENABLED"] = "true"

        errors = MODULE.validate(self.root, self.contract, profile)

        self.assertIn(
            "production-like profile mismatch: ADP_LOCAL_FIXTURES_ENABLED expected=false actual=true",
            errors,
        )


if __name__ == "__main__":
    unittest.main()
