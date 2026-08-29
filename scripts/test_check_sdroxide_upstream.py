# SPDX-License-Identifier: GPL-3.0-only
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_sdroxide_upstream as watcher


PIN = {
    "repository": "https://github.com/dividebysandwich/sdroxide",
    "releaseVersion": "1.5.4",
    "releaseTag": "v1.5.4",
    "reviewedCommit": "1f62978036aaa0e3e9f80bca5db4c19102962fd7",
    "reviewedTree": "77a8a562e7c44d7cc9a77cec3169aeba13bc83d3",
    "license": "GPL-3.0",
    "licenseSha256": "3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986",
}


class SdroxideWatcherTest(unittest.TestCase):
    def current(self, **overrides):
        value = {
            "releaseVersion": PIN["releaseVersion"],
            "releaseTag": PIN["releaseTag"],
            "commit": PIN["reviewedCommit"],
            "tree": PIN["reviewedTree"],
            "licenseSha256": PIN["licenseSha256"],
            "files": [],
        }
        value.update(overrides)
        return value

    def test_reviewed_release_is_accepted_without_changes(self):
        report = watcher.compare(PIN, self.current())

        self.assertEqual("NO_CHANGE", report["result"])
        self.assertEqual([], report["relevantChanges"])
        self.assertTrue(report["readOnly"])
        self.assertFalse(report["automaticUpdate"])
        self.assertFalse(report["automaticPullRequest"])

    def test_any_identity_drift_requires_review_and_keeps_file_classification(self):
        changed = [{"path": "crates/sdroxide-tci/src/protocol.rs", "status": "modified", "category": "TCI"}]
        report = watcher.compare(PIN, self.current(commit="next", files=changed))

        self.assertEqual("REVIEW_REQUIRED", report["result"])
        self.assertEqual(changed, report["relevantChanges"])
        self.assertEqual("LICENCE", watcher.classify("vendor/example/LICENSE"))
        self.assertEqual("RDS_RBDS", watcher.classify("src/rds.rs"))

    def test_cli_fixture_returns_zero_for_the_reviewed_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            pin_path = root / "pin.json"
            fixture_path = root / "fixture.json"
            output_path = root / "report.json"
            pin_path.write_text(json.dumps(PIN), encoding="utf-8")
            fixture_path.write_text(json.dumps(self.current()), encoding="utf-8")
            with mock.patch("sys.argv", [
                "check_sdroxide_upstream.py", "--pin", str(pin_path),
                "--fixture", str(fixture_path), "--json-output", str(output_path),
            ]):
                self.assertEqual(0, watcher.main())
            self.assertEqual("NO_CHANGE", json.loads(output_path.read_text(encoding="utf-8"))["result"])


if __name__ == "__main__":
    unittest.main()
