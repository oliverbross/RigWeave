#!/usr/bin/env python3
"""Deterministic APK/AAB package-size and prohibited-payload audit."""

from __future__ import annotations

import argparse
import hashlib
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

APK_REFERENCE = 109_820_912
FINISHLINE_APK_REFERENCE = 110_106_490
PROHIBITED = ("coeff01w", "libp533", "iturhfprop", "itu-r-hf")


def category(name: str) -> str:
    lower = name.lower()
    if lower.endswith(".dex"):
        return "dex"
    if lower.startswith(("res/", "resources/", "base/res/")):
        return "resources"
    if lower.startswith(("assets/", "base/assets/")):
        return "assets"
    if "/lib/" in f"/{lower}" or lower.startswith("lib/"):
        return "native"
    return "other"


def audit(path: Path) -> bool:
    with zipfile.ZipFile(path) as archive:
        rows = sorted((item for item in archive.infolist() if not item.is_dir()), key=lambda item: item.filename)
        compressed = sum(item.compress_size for item in rows)
        uncompressed = sum(item.file_size for item in rows)
        totals = defaultdict(int)
        abi = defaultdict(int)
        basenames = defaultdict(list)
        hashes = defaultdict(list)
        prohibited = []
        for item in rows:
            totals[category(item.filename)] += item.file_size
            parts = item.filename.split("/")
            if "lib" in parts:
                index = parts.index("lib")
                if index + 1 < len(parts):
                    abi[parts[index + 1]] += item.file_size
            basenames[Path(item.filename).name].append(item.filename)
            if any(token in item.filename.lower() for token in PROHIBITED):
                prohibited.append(item.filename)
            if item.file_size:
                hashes[hashlib.sha256(archive.read(item)).hexdigest()].append(item.filename)

        print(f"\n## {path.name}")
        print(f"path: {path.resolve()}")
        print(f"archive bytes: {path.stat().st_size}")
        print(f"ZIP compressed bytes: {compressed}")
        print(f"ZIP uncompressed bytes: {uncompressed}")
        if path.suffix.lower() == ".apk":
            print(f"delta vs combined integration APK: {path.stat().st_size - APK_REFERENCE:+d}")
            print(f"delta vs Finish-Line APK: {path.stat().st_size - FINISHLINE_APK_REFERENCE:+d}")
        for key in ("dex", "resources", "assets", "native", "other"):
            print(f"{key} uncompressed bytes: {totals[key]}")
        print("native libraries by ABI: " + (", ".join(f"{key}={value}" for key, value in sorted(abi.items())) or "none"))

        print("top 40 entries:")
        for item in sorted(rows, key=lambda item: (-item.file_size, item.filename))[:40]:
            print(f"  {item.file_size:12d} {item.compress_size:12d} {item.filename}")
        print("files over 1 MB:")
        for item in sorted((item for item in rows if item.file_size > 1_000_000), key=lambda item: (-item.file_size, item.filename)):
            print(f"  {item.file_size:12d} {item.filename}")

        repeated = {name: values for name, values in basenames.items() if len(values) > 1}
        identical = [values for values in hashes.values() if len(values) > 1]
        print(f"repeated basenames: {len(repeated)}")
        for name, values in sorted(repeated.items())[:40]:
            print(f"  {name}: {', '.join(values)}")
        print(f"identical payload groups: {len(identical)}")
        for values in sorted(identical, key=lambda values: values[0])[:40]:
            print(f"  {', '.join(values)}")
        print("ITU/P533 payload scan: " + ("FAIL " + ", ".join(prohibited) if prohibited else "PASS"))
        return not prohibited


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("archives", nargs="+", type=Path)
    args = parser.parse_args()
    missing = [path for path in args.archives if not path.is_file()]
    if missing:
        parser.error("missing archive(s): " + ", ".join(map(str, missing)))
    passed = all(audit(path) for path in args.archives)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
