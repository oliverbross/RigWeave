import subprocess
import unittest
from pathlib import Path


class FinalRcReleaseContractTest(unittest.TestCase):
    def test_checker_passes_current_release_tree(self):
        root = Path(__file__).resolve().parents[1]
        completed = subprocess.run(["python3", "scripts/check_final_rc1_release.py"],
                                   cwd=root, text=True, capture_output=True)
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        self.assertIn("contract PASS", completed.stdout)


if __name__ == "__main__":
    unittest.main()
