#!/usr/bin/env python3
import importlib.util
import pathlib
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name("check_android_tci_transmit_v5.py")
SPEC = importlib.util.spec_from_file_location("watcher", SCRIPT)
watcher = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(watcher)


class WatcherTest(unittest.TestCase):
    def test_repository_passes(self):
        self.assertEqual([], watcher.check(SCRIPT.parents[1]))

    def test_missing_tree_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            failures = watcher.check(pathlib.Path(directory))
        self.assertTrue(any(item.startswith("missing ") for item in failures))


if __name__ == "__main__":
    unittest.main()
