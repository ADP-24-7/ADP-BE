#!/usr/bin/env python3
"""Fail closed when the local integration checkout differs from the frozen lock."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path


LOCK_RELATIVE_PATH = "config/integration/repository-lock.json"
PROFILES = {"local", "demo", "production-like"}


def git(repo: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(repo), *args],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def validate(lock_path: Path, profile: str, allow_dirty: bool = False) -> list[str]:
    errors: list[str] = []
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    if lock.get("schemaVersion") != "adp-local-integration-lock/v1":
        errors.append("unsupported integration lock schema")

    be_root = lock_path.parents[2]
    workspace_root = be_root.parent
    profile_path = be_root / "config" / "integration" / "profiles" / f"{profile}.env"
    if profile not in PROFILES or not profile_path.is_file():
        errors.append(f"unknown or missing integration profile: {profile}")

    for repository in lock.get("repositories", []):
        name = repository["name"]
        repo = workspace_root / repository["path"]
        expected = repository["commit"]
        if not (repo / ".git").exists():
            errors.append(f"{name}: repository not found at {repo}")
            continue
        try:
            current = git(repo, "rev-parse", "HEAD")
            git(repo, "cat-file", "-e", f"{expected}^{{commit}}")
        except subprocess.CalledProcessError:
            errors.append(f"{name}: expected commit is unavailable: {expected}")
            continue

        if current != expected:
            if repository.get("selfHosted"):
                changed = git(repo, "diff", "--name-only", expected, current).splitlines()
                unexpected = [path for path in changed if path != LOCK_RELATIVE_PATH]
                if unexpected:
                    errors.append(
                        f"{name}: source differs from locked commit {expected[:12]} "
                        f"({', '.join(unexpected[:5])})"
                    )
            else:
                errors.append(f"{name}: expected {expected[:12]}, found {current[:12]}")

        if not allow_dirty:
            dirty = git(repo, "status", "--porcelain", "--untracked-files=no")
            if dirty:
                errors.append(f"{name}: tracked working tree changes are not reproducible")

    for relative in lock.get("requiredFiles", []):
        if not (workspace_root / relative).is_file():
            errors.append(f"required integration input is missing: {relative}")

    expected_migration = str(lock.get("database", {}).get("latestFlywayVersion", ""))
    migrations = list((be_root / "src" / "main" / "resources" / "db" / "migration").glob("V*__*.sql"))
    versions = {path.name[1:].split("__", 1)[0] for path in migrations}
    if expected_migration not in versions:
        errors.append(f"Flyway V{expected_migration} is not present")
    numeric_versions = [int(version) for version in versions if version.isdigit()]
    if numeric_versions and str(max(numeric_versions)) != expected_migration:
        errors.append(
            f"Flyway lock expects V{expected_migration}, repository latest is V{max(numeric_versions)}"
        )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--lock", type=Path, default=Path(LOCK_RELATIVE_PATH))
    parser.add_argument("--profile", default="demo")
    parser.add_argument("--allow-dirty", action="store_true")
    args = parser.parse_args()

    errors = validate(args.lock.resolve(), args.profile, args.allow_dirty)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"Integration lock valid for profile={args.profile}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
