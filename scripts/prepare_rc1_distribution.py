#!/usr/bin/env python3
"""Create deterministic exact-SHA source, manifest, SBOM and digest artifacts."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = {"qso": 16, "neural": 5, "contest": 2, "digi": 2, "groups_io": 2, "dx_chaser": 1}
EXPECTED_NAMES = {
    "android_apk": "RigWeave-Android-arm64-v8a-{sha}.apk",
    "android_aab": "RigWeave-Android-four-ABI-{sha}.aab",
    "windows_zip": "RigWeave-Windows-x64-portable-{sha}.zip",
    "windows_setup": "RigWeave-Windows-x64-setup-{sha}.exe",
    "macos_zip": "RigWeave-macOS-arm64-unsigned-{sha}.zip",
    "source": "RigWeave-source-{sha}.tar.gz",
}


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "build/rc1/distribution")
    parser.add_argument("--channel", default="RC")
    parser.add_argument("--build-utc")
    parser.add_argument("--collect", action="append", default=[], metavar="KIND=PATH")
    parser.add_argument("--allow-dirty", action="store_true")
    args = parser.parse_args()

    sha = git("rev-parse", "HEAD")
    if not args.allow_dirty and git("status", "--porcelain"):
        raise SystemExit("distribution requires a clean exact-SHA worktree")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    build_utc = args.build_utc or datetime.now(timezone.utc).replace(microsecond=0).isoformat()

    source_name = EXPECTED_NAMES["source"].format(sha=sha)
    subprocess.check_call(
        ["git", "archive", "--format=tar.gz", f"--prefix=RigWeave-{sha}/", "-o", str(output / source_name), sha],
        cwd=ROOT,
    )

    files = []
    for name in git("ls-tree", "-r", "--name-only", sha).splitlines():
        blob = subprocess.check_output(["git", "show", f"{sha}:{name}"], cwd=ROOT)
        files.append({"path": name, "sha256": hashlib.sha256(blob).hexdigest(), "bytes": len(blob)})
    manifest = {
        "contract": "RIGWEAVE_SOURCE_MANIFEST_V1",
        "sha": sha,
        "channel": args.channel,
        "build_utc": build_utc,
        "schemas": SCHEMAS,
        "files": files,
    }
    (output / "SOURCE_MANIFEST.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")

    packages = [
        ("RigWeave", "NOASSERTION"), ("Hamlib", "4.7.2"), ("SDRoxide", "vendored-record"),
        ("mfsk-core", "vendored"), ("tempo-sstv", "vendored"), ("SGP4", "vendored"),
        ("ITUHFProp", "vendored"), ("CTY", "data-snapshot"), ("BandPlans", "data-snapshot"),
    ]
    sbom = {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": "SPDXRef-DOCUMENT",
        "name": f"RigWeave-{sha}",
        "documentNamespace": f"https://rigweave.app/spdx/{sha}",
        "creationInfo": {"created": build_utc, "creators": ["Tool: scripts/prepare_rc1_distribution.py"]},
        "packages": [
            {"name": name, "SPDXID": f"SPDXRef-Package-{index}", "versionInfo": version,
             "downloadLocation": "NOASSERTION", "filesAnalyzed": False,
             "licenseConcluded": "NOASSERTION", "licenseDeclared": "NOASSERTION"}
            for index, (name, version) in enumerate(packages)
        ],
    }
    (output / "SBOM.spdx.json").write_text(json.dumps(sbom, indent=2, sort_keys=True) + "\n")

    collected = {}
    for item in args.collect:
        kind, separator, raw_path = item.partition("=")
        if not separator or kind not in EXPECTED_NAMES or kind == "source":
            raise SystemExit(f"invalid --collect value: {item}")
        source = Path(raw_path).resolve()
        if not source.is_file():
            raise SystemExit(f"artifact not found: {source}")
        destination = output / EXPECTED_NAMES[kind].format(sha=sha)
        shutil.copy2(source, destination)
        collected[kind] = destination.name

    build = {
        "contract": "RIGWEAVE_RC1_BUILD_MANIFEST_V1", "sha": sha, "channel": args.channel,
        "build_utc": build_utc, "schemas": SCHEMAS, "platforms": ["Android", "iOS", "Windows", "macOS"],
        "expected_artifacts": {key: value.format(sha=sha) for key, value in EXPECTED_NAMES.items()},
        "collected_artifacts": collected,
    }
    (output / "BUILD_MANIFEST.json").write_text(json.dumps(build, indent=2, sort_keys=True) + "\n")

    artifacts = sorted(path for path in output.iterdir() if path.is_file() and path.name != "SHA256SUMS.txt")
    sums = "".join(f"{digest(path)}  {path.name}\n" for path in artifacts)
    (output / "SHA256SUMS.txt").write_text(sums)
    print(json.dumps({"sha": sha, "output": str(output), "artifacts": [path.name for path in artifacts]}, indent=2))


if __name__ == "__main__":
    main()
