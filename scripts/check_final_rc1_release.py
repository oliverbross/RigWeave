#!/usr/bin/env python3
"""Fail-closed source, lineage, parity, packaging and release-contract audit."""

from __future__ import annotations

from pathlib import Path
import json
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
START = "a4c3760622a0d7c8eda34bc039a852ac933542a8"
RECOVERY = "27c70d0c2ab0ae21ef18d7fd9b39f8878b0940ea"
RC1_TAG = "8c085e979166d083283177d731a662a5424c7478"
ANDROID_FINAL_BRANCH = "integration/android-v1-final"
REQUIRED_DOCS = [
    "RIGWEAVE_V0_1_0_RC1.md", "FINAL_CONSOLIDATION_REPORT.md",
    "FINAL_LINEAGE_AUDIT_V0_1_0_RC1.md", "FINAL_BRANCH_CLASSIFICATION_V0_1_0_RC1.json",
    "FINAL_UNIQUE_WORK_LEDGER_V0_1_0_RC1.md", "FINAL_PLATFORM_PARITY_V0_1_0_RC1.md",
    "FINAL_OWNER_GRAPH_V0_1_0_RC1.md", "FINAL_SCHEMA_MATRIX_V0_1_0_RC1.md",
    "FINAL_FEATURE_MATRIX_V0_1_0_RC1.md", "GITHUB_RELEASE_V0_1_0_RC1.md",
    "INSTALL_ANDROID_RC1.md", "INSTALL_WINDOWS_RC1.md", "INSTALL_MACOS_RC1.md",
    "INSTALL_LINUX_RC1.md", "IOS_RC1_BUILD_AND_SIGNING_BOUNDARY.md",
    "EXECUTED_BRANCH_CLEANUP_V0_1_0_RC1.md", "POST_RC1_LIVE_ACCEPTANCE.md",
]
ASSETS = [
    "RigWeave-Android-arm64-v0.1.0-rc.1.apk",
    "RigWeave-Android-four-ABI-v0.1.0-rc.1.aab",
    "RigWeave-iOS-Simulator-v0.1.0-rc.1.zip",
    "RigWeave-iOS-unsigned-XCArchive-v0.1.0-rc.1.zip",
    "RigWeave-macOS-arm64-unsigned-v0.1.0-rc.1.zip",
    "RigWeave-Windows-x64-portable-v0.1.0-rc.1.zip",
    "RigWeave-Windows-x64-setup-v0.1.0-rc.1.exe",
    "RigWeave-Linux-x86_64-v0.1.0-rc.1.tar.gz",
    "RigWeave-Linux-x86_64-v0.1.0-rc.1.deb",
    "RigWeave-stationd-Linux-x86_64-v0.1.0-rc.1.tar.gz",
    "RigWeave-stationd-Linux-aarch64-v0.1.0-rc.1.tar.gz",
    "RigWeave-source-v0.1.0-rc.1.tar.gz",
]


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def main() -> None:
    branch = git("branch", "--show-current")
    allow_descendant = os.environ.get("RIGWEAVE_ALLOW_RC1_DESCENDANT_VALIDATION") == "1"
    release_branches = {"integration/rigweave-v0.1.0-rc1-final", "main"}
    require(branch in release_branches or (allow_descendant and branch == ANDROID_FINAL_BRANCH),
            f"unexpected release branch: {branch}")
    require(subprocess.run(["git", "merge-base", "--is-ancestor", START, "HEAD"], cwd=ROOT).returncode == 0,
            "secure-remote-station v6 is not an ancestor")
    require(git("rev-parse", "v0.1.0-rc.1^{}") == RC1_TAG, "published RC1 tag moved")
    require(git("rev-parse", "refs/remotes/origin/recovery/local-main-27c70d0") == RECOVERY,
            "protected recovery ref moved")
    for name in REQUIRED_DOCS:
        require((ROOT / "docs/release" / name).is_file(), f"missing release document: {name}")
    classification = json.loads((ROOT / "docs/release/FINAL_BRANCH_CLASSIFICATION_V0_1_0_RC1.json").read_text())
    require(len(classification["refs"]) >= 90, "whole-repository classification is incomplete")
    require(any(row["classification"] == "KEEP_RECOVERY" for row in classification["refs"]),
            "recovery classification is absent")

    cmake = (ROOT / "desktop/CMakeLists.txt").read_text()
    require("ddbe48383984d56acd9e1ab6a090c54ca6b735a6" in cmake and "libsecret-1" in cmake,
            "pinned Opus or Linux credential dependency missing")
    for path in ["desktop/src/network/RemoteStationClient.cpp", "ios/RigWeave/RemoteStationClient.swift",
                 "android/app/src/main/java/app/rigweave/mobile/ControlSurfaceController.kt"]:
        require((ROOT / path).is_file(), f"missing final parity owner: {path}")
    matrix = (ROOT / "docs/release/FINAL_FEATURE_MATRIX_V0_1_0_RC1.md").read_text()
    require("FOUNDATION_WIRED=0" in matrix and "MISSING=0" in matrix, "final feature matrix is not closed")
    require("CURRENT_PROJECT_VERSION = 39" in (ROOT / "ios/RigWeave.xcodeproj/project.pbxproj").read_text(),
            "Apple build number is not 39")
    android = (ROOT / "android/app/build.gradle.kts").read_text()
    rc_android = "versionCode = 39" in android and 'versionName = "0.1.0-rc.1"' in android
    android_1 = "versionCode = 40" in android and 'versionName = "1.0.0"' in android
    require(rc_android or android_1, "Android release identity is neither published RC1 nor Android 1.0")
    if branch == ANDROID_FINAL_BRANCH:
        require(android_1, "Android final branch does not declare version 1.0.0 / code 40")

    distribution = (ROOT / "scripts/prepare_rc1_distribution.py").read_text()
    for asset in ASSETS:
        require(asset in distribution, f"artifact matrix is missing {asset}")
    workflow = (ROOT / ".github/workflows/rigweave-multiplatform-candidate.yml").read_text()
    for marker in ["linux-x64-full-gui", "linux-arm64-stationd", "ubuntu-24.04-arm",
                   "integration/rigweave-v0.1.0-rc1-final", ANDROID_FINAL_BRANCH, "v0.1.0-rc.1"]:
        require(marker in workflow, f"authoritative workflow missing {marker}")
    print("final RigWeave 0.1.0 RC1 source, lineage, parity, package and workflow contract PASS")


if __name__ == "__main__":
    main()
