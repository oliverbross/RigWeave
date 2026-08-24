#!/usr/bin/env python3
"""Install the exact open-source Qt 6.11.2 MinGW archives from download.qt.io.

Qt 6.11.2 introduced an architecture-split repository directory which aqtinstall
3.3.0 cannot discover yet.  This bootstrapper keeps aqt's normal archive patching
while resolving and verifying the official packages from that split directory.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

from aqt.archives import TargetConfig
from aqt.updater import Updater


REPOSITORY = (
    "https://download.qt.io/online/qtsdkrepository/windows_x86/desktop/"
    "qt6_6112/qt6_6112_mingw"
)
PACKAGES = (
    "qt.qt6.6112.win64_mingw",
    "qt.qt6.6112.addons.qtmultimedia.win64_mingw",
    "qt.qt6.6112.addons.qtserialport.win64_mingw",
    "qt.qt6.6112.addons.qtwebsockets.win64_mingw",
    "qt.qt6.6112.addons.qtlocation.win64_mingw",
    "qt.qt6.6112.addons.qtpositioning.win64_mingw",
)


def read_url(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=120) as response:
        return response.read()


def download_one(item: tuple[str, str, Path]) -> Path:
    url, sha1_url, destination = item
    expected = read_url(sha1_url).decode("ascii").split()[0].lower()
    if destination.exists():
        actual = hashlib.sha1(destination.read_bytes()).hexdigest()
        if actual == expected:
            return destination

    temporary = destination.with_suffix(destination.suffix + ".part")
    digest = hashlib.sha1()
    with urllib.request.urlopen(url, timeout=300) as response, temporary.open("wb") as output:
        while chunk := response.read(1024 * 1024):
            output.write(chunk)
            digest.update(chunk)
    if digest.hexdigest() != expected:
        temporary.unlink(missing_ok=True)
        raise RuntimeError(f"SHA-1 mismatch for {url}")
    temporary.replace(destination)
    return destination


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--cache", required=True, type=Path)
    args = parser.parse_args()

    root = ET.fromstring(read_url(f"{REPOSITORY}/Updates.xml"))
    metadata = {
        package.findtext("Name", ""): package for package in root.findall("PackageUpdate")
    }
    args.output.mkdir(parents=True, exist_ok=True)
    qt_root = args.output / "6.11.2" / "mingw_64"
    qt_root.mkdir(parents=True, exist_ok=True)
    args.cache.mkdir(parents=True, exist_ok=True)

    downloads: list[tuple[str, str, Path]] = []
    for package_name in PACKAGES:
        package = metadata.get(package_name)
        if package is None:
            raise RuntimeError(f"Required package absent from official metadata: {package_name}")
        version = package.findtext("Version", "")
        archives = [value.strip() for value in package.findtext("DownloadableArchives", "").split(",")]
        for archive in filter(None, archives):
            remote_name = f"{version}{archive}"
            url = f"{REPOSITORY}/{package_name}/{remote_name}"
            downloads.append((url, f"{url}.sha1", args.cache / remote_name))

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        archives = list(executor.map(download_one, downloads))
    for archive in archives:
        subprocess.run(
            ["7z", "x", "-y", f"-o{qt_root}", str(archive)],
            check=True,
            stdout=subprocess.DEVNULL,
        )

    Updater.update(
        TargetConfig("6.11.2", "desktop", "win64_mingw", "windows"),
        args.output,
        None,
    )
    required = ("bin/qmake.exe", "lib/cmake/Qt6/Qt6Config.cmake", "bin/Qt6Core.dll")
    missing = [path for path in required if not (qt_root / path).is_file()]
    if missing:
        raise RuntimeError(f"Qt archive verification failed; missing: {', '.join(missing)}")
    print(qt_root)


if __name__ == "__main__":
    main()
