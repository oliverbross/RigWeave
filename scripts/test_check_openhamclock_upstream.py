import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from scripts import check_openhamclock_upstream as watcher


STABLE = "1" * 40
PREVIEW = "2" * 40
RELEASE = "3" * 40
NEXT_PREVIEW = "4" * 40
NEXT_RELEASE = "5" * 40
LICENCE = b"MIT test licence\n"
LICENCE_DIGEST = watcher.sha256(LICENCE)


def comparison(
    status="ahead",
    commits=None,
    files=None,
    total_commits=None,
):
    commits = commits or []
    return {
        "status": status,
        "ahead_by": len(commits),
        "behind_by": 0,
        "total_commits": len(commits) if total_commits is None else total_commits,
        "commits": commits,
        "files": files or [],
    }


def commit(commit_sha, message):
    return {"sha": commit_sha, "commit": {"message": message}}


def manifest():
    return {
        "upstream": {"owner": "accius", "repository": "openhamclock"},
        "stable": {
            "branch": "main",
            "sha": STABLE,
            "package_version": "26.5.0",
        },
        "preview": {"branch": "Staging", "sha": PREVIEW},
        "release": {
            "tag": "v26.5.0",
            "commit": RELEASE,
            "package_version": "26.5.0",
            "licence_sha256": LICENCE_DIGEST,
        },
        "licence": {"file": "LICENSE", "sha256": LICENCE_DIGEST},
        "watched_source_paths": {
            "inventory": ["src/plugins/layers/"],
            "providers": ["server/routes/", "src/services/"],
            "propagation_algorithms": ["server/utils/propagationPhysics.js"],
            "security": ["server/middleware/", "SECURITY.md"],
            "licence": ["LICENSE"],
        },
    }


class FakeGitHub:
    def __init__(self):
        self.branches = {"main": STABLE, "Staging": PREVIEW}
        self.release = {
            "tag_name": "v26.5.0",
            "published_at": "2026-07-08T19:23:05Z",
        }
        self.tag_object = {"type": "tag", "sha": "6" * 40}
        self.annotated_object = {"type": "commit", "sha": RELEASE}
        self.comparisons = {
            f"{STABLE}...{PREVIEW}": comparison("diverged"),
        }
        package = json.dumps({"version": "26.5.0"}).encode()
        self.contents = {
            ("package.json", STABLE): package,
            ("LICENSE", STABLE): LICENCE,
            ("package.json", RELEASE): package,
            ("LICENSE", RELEASE): LICENCE,
        }

    def request(self, path, **_kwargs):
        clean = path.split("?", 1)[0]
        if "/branches/" in clean:
            branch = clean.rsplit("/", 1)[1]
            return {"commit": {"sha": self.branches[branch]}}
        if clean.endswith("/releases/latest"):
            return self.release
        if "/git/ref/tags/" in clean:
            return {"object": self.tag_object}
        if "/git/tags/" in clean:
            return {"object": self.annotated_object}
        if "/compare/" in clean:
            return self.comparisons[clean.split("/compare/", 1)[1]]
        raise AssertionError(f"Unexpected fake request: {path}")

    def content(self, _repo, path, ref):
        return self.contents[(path, ref)]


class OpenHamClockWatcherTest(unittest.TestCase):
    def test_no_change_requires_no_review(self):
        result = watcher.audit(FakeGitHub(), manifest())
        self.assertEqual("NO_REVIEW", result["result"])
        self.assertFalse(result["release"]["changed"])

    def test_release_only_change_requires_review(self):
        client = FakeGitHub()
        client.release["tag_name"] = "v26.6.0"
        client.tag_object = {"type": "commit", "sha": NEXT_RELEASE}
        client.comparisons[f"{RELEASE}...{NEXT_RELEASE}"] = comparison(
            commits=[commit(NEXT_RELEASE, "release 26.6.0")],
            files=[{"filename": "package.json"}],
        )
        client.contents[("package.json", NEXT_RELEASE)] = json.dumps(
            {"version": "26.6.0"}
        ).encode()
        client.contents[("LICENSE", NEXT_RELEASE)] = LICENCE
        result = watcher.audit(client, manifest())
        self.assertEqual("REVIEW_REQUIRED", result["result"])
        self.assertTrue(result["release_only_change"])
        self.assertIn("commit", result["release"]["changed_fields"])

    def test_preview_security_commit_under_layers_is_detected(self):
        client = FakeGitHub()
        client.branches["Staging"] = NEXT_PREVIEW
        client.comparisons[f"{PREVIEW}...{NEXT_PREVIEW}"] = comparison(
            commits=[
                commit(
                    NEXT_PREVIEW,
                    "fix(layers): escape satellite name before rendering",
                )
            ],
            files=[{"filename": "src/plugins/layers/satellites.jsx"}],
        )
        client.comparisons[f"{STABLE}...{NEXT_PREVIEW}"] = comparison("diverged")
        result = watcher.audit(client, manifest())
        triggers = result["preview"]["classification"]["triggering_categories"]
        self.assertIn("security", triggers)
        self.assertEqual(NEXT_PREVIEW, triggers["security"]["commits"][0]["sha"])

    def test_provider_contract_commit_is_detected(self):
        client = FakeGitHub()
        client.branches["Staging"] = NEXT_PREVIEW
        client.comparisons[f"{PREVIEW}...{NEXT_PREVIEW}"] = comparison(
            commits=[commit(NEXT_PREVIEW, "change provider API contract schema")],
            files=[{"filename": "server/routes/solar.js"}],
        )
        client.comparisons[f"{STABLE}...{NEXT_PREVIEW}"] = comparison("diverged")
        result = watcher.audit(client, manifest())
        categories = result["preview"]["classification"]["triggering_categories"]
        self.assertIn("provider_contract", categories)
        self.assertEqual("REVIEW_REQUIRED", result["result"])

    def test_stable_licence_digest_change_requires_review(self):
        client = FakeGitHub()
        client.contents[("LICENSE", STABLE)] = b"changed licence\n"
        result = watcher.audit(client, manifest())
        self.assertTrue(result["stable"]["licence_changed"])
        self.assertIn("stable licence digest changed", result["review_reasons"])

    def test_malformed_or_rate_limited_comparison_returns_exit_one(self):
        class FailingGitHub:
            def __init__(self, _token=None):
                pass

            def request(self, _path, **_kwargs):
                raise watcher.AuditError("GitHub API HTTP 429")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest_path = root / "upstream.json"
            manifest_path.write_text(json.dumps(manifest()))
            output = root / "reports"
            with mock.patch.object(watcher, "GitHub", FailingGitHub):
                code = watcher.main(
                    ["--manifest", str(manifest_path), "--output-dir", str(output)]
                )
            self.assertEqual(watcher.EXIT_COMPARISON_FAILED, code)
            report = json.loads(
                (output / "openhamclock-upstream-audit.json").read_text()
            )
            self.assertEqual("COMPARISON_FAILED", report["result"])

    def test_truncated_changed_preview_requires_review(self):
        client = FakeGitHub()
        client.branches["Staging"] = NEXT_PREVIEW
        commits = [commit(f"{index:040x}", f"ordinary change {index}") for index in range(100)]
        client.comparisons[f"{PREVIEW}...{NEXT_PREVIEW}"] = comparison(
            commits=commits,
            files=[{"filename": "README.md"}],
            total_commits=101,
        )
        client.comparisons[f"{STABLE}...{NEXT_PREVIEW}"] = comparison("diverged")
        result = watcher.audit(client, manifest())
        self.assertTrue(result["preview"]["comparison"]["truncated"])
        self.assertIn("preview inventory was truncated", result["review_reasons"])


if __name__ == "__main__":
    unittest.main()
