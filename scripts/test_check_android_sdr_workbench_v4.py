#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only

import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import check_android_sdr_workbench_v4 as checker


class AndroidSdrWorkbenchV4AuditTest(unittest.TestCase):
    def test_repository_contract_passes(self) -> None:
        checker.audit()

    def test_implement_v4_rows_fail_closed(self) -> None:
        self.assertEqual([], checker.implement_v4_rows("| Family | PARITY | Evidence |"))
        self.assertEqual(["| Recording | IMPLEMENT_V4 | missing |"],
                         checker.implement_v4_rows("| Recording | IMPLEMENT_V4 | missing |"))

    def test_workbench_strip_count_fail_closed(self) -> None:
        call = "SdrStereoWorkbenchStrip(workbench, controller, radio, localReceivers)"
        self.assertEqual(1, checker.workbench_strip_calls(call))
        self.assertEqual(2, checker.workbench_strip_calls(f"{call}\n{call}"))


if __name__ == "__main__":
    unittest.main()
