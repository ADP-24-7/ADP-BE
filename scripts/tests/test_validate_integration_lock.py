import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "validate-integration-lock.py"
SPEC = importlib.util.spec_from_file_location("validate_integration_lock", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class IntegrationLockValidatorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.workspace = Path(self.temp.name)
        self.be = self.create_repo("ADP-BE")
        self.fe = self.create_repo("ADP-FE")
        (self.be / "config/integration/profiles").mkdir(parents=True)
        (self.be / "config/integration/profiles/demo.env").write_text("PROFILE=demo\n")
        (self.be / "src/main/resources/db/migration").mkdir(parents=True)
        (self.be / "src/main/resources/db/migration/V49__current.sql").write_text("select 1;\n")
        (self.fe / "package-lock.json").write_text("{}\n")
        self.commit(self.be, "inputs")
        self.commit(self.fe, "inputs")
        self.lock_path = self.be / MODULE.LOCK_RELATIVE_PATH
        self.lock_path.parent.mkdir(parents=True, exist_ok=True)

    def tearDown(self):
        self.temp.cleanup()

    def create_repo(self, name):
        repo = self.workspace / name
        repo.mkdir()
        subprocess.run(["git", "init", "-q", repo], check=True)
        subprocess.run(["git", "-C", repo, "config", "user.email", "test@example.com"], check=True)
        subprocess.run(["git", "-C", repo, "config", "user.name", "Test"], check=True)
        (repo / "README.md").write_text(name)
        self.commit(repo, "initial")
        return repo

    def commit(self, repo, message):
        subprocess.run(["git", "-C", repo, "add", "."], check=True)
        subprocess.run(["git", "-C", repo, "commit", "-qm", message], check=True)

    def head(self, repo):
        return subprocess.run(
            ["git", "-C", repo, "rev-parse", "HEAD"], check=True, capture_output=True, text=True
        ).stdout.strip()

    def write_lock(self, fe_commit=None):
        self.lock_path.write_text(json.dumps({
            "schemaVersion": "adp-local-integration-lock/v1",
            "repositories": [
                {"name": "ADP-BE", "path": "ADP-BE", "commit": self.head(self.be), "selfHosted": True},
                {"name": "ADP-FE", "path": "ADP-FE", "commit": fe_commit or self.head(self.fe)},
            ],
            "database": {"latestFlywayVersion": "49"},
            "requiredFiles": ["ADP-FE/package-lock.json"],
        }))

    def test_accepts_exact_locked_repositories(self):
        self.write_lock()
        self.assertEqual([], MODULE.validate(self.lock_path, "demo", allow_dirty=True))

    def test_rejects_peer_repository_commit_drift(self):
        locked_fe = self.head(self.fe)
        (self.fe / "README.md").write_text("changed")
        self.commit(self.fe, "drift")
        self.write_lock(fe_commit=locked_fe)
        errors = MODULE.validate(self.lock_path, "demo", allow_dirty=True)
        self.assertTrue(any("ADP-FE: expected" in error for error in errors))

    def test_rejects_dirty_tracked_files(self):
        self.write_lock()
        (self.fe / "README.md").write_text("dirty")
        errors = MODULE.validate(self.lock_path, "demo")
        self.assertIn("ADP-FE: tracked working tree changes are not reproducible", errors)

    def test_rejects_flyway_version_drift(self):
        self.write_lock()
        (self.be / "src/main/resources/db/migration/V50__unexpected.sql").write_text("select 1;\n")
        errors = MODULE.validate(self.lock_path, "demo", allow_dirty=True)
        self.assertIn("Flyway lock expects V49, repository latest is V50", errors)


if __name__ == "__main__":
    unittest.main()
