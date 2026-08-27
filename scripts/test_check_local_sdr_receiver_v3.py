# SPDX-License-Identifier: GPL-3.0-only
import subprocess
import sys
import unittest
from pathlib import Path


class LocalSdrReceiverV3AuditTest(unittest.TestCase):
    def test_repository_contract(self) -> None:
        root = Path(__file__).resolve().parents[1]
        result = subprocess.run([sys.executable, str(root / "scripts/check_local_sdr_receiver_v3.py")], cwd=root,
                                text=True, capture_output=True, check=False)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
