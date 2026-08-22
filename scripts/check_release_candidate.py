#!/usr/bin/env python3
"""Deterministic, credential-free release-candidate contract audit."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
REQUIRED_DOCS = [
    "RIGWEAVE_FINAL_CONVERGENCE.md",
    "FINAL_CORE_COMPLETION_MATRIX.md",
    "FINAL_CORE_OWNERSHIP.md",
    "RELEASE_CANDIDATE_READINESS.md",
    "PRIVACY_AND_DATA_INVENTORY.md",
    "THIRD_PARTY_AND_PROVENANCE_AUDIT.md",
    "FINAL_LIVE_ACCEPTANCE_CHECKLIST.md",
]
SCHEMA_PATTERNS = {
    "QSO schema 13": r"class QsoDatabase[^\n]+SQLiteOpenHelper\(context, databaseName, null, 13\)",
    "Neural schema 5": r"SQLiteOpenHelper\(context, databaseName, null, 5\)",
    "Digi schema 2": r"SQLiteOpenHelper\(context, databaseName, null, 2\)",
    "Groups.io schema 2": r"SQLiteOpenHelper\(appContext, databaseName, null, 2\)",
    "projection contract 2": r"QSO schema 13 · projection contract 2 · Neural 5 · Digi 2 · Groups\.io 2",
}


def fail(message: str) -> None:
    raise SystemExit(f"release-candidate audit failed: {message}")


for name in REQUIRED_DOCS:
    if not (ROOT / "docs" / name).is_file():
        fail(f"missing docs/{name}")

source = "\n".join(
    path.read_text(errors="replace")
    for path in (ROOT / "android/app/src/main/java/app/rigweave/mobile").rglob("*.kt")
)
for label, pattern in SCHEMA_PATTERNS.items():
    if not re.search(pattern, source):
        fail(f"{label} source contract not found")

fixture_path = ROOT / "fixtures/configuration/golden-v1.json"
fixture = json.loads(fixture_path.read_text())
if fixture.get("format_signature") != "RIGWEAVE_CONFIGURATION_BUNDLE" or fixture.get("schema") != 1:
    fail("configuration fixture signature/schema mismatch")
canonical = json.dumps(fixture["sections"], separators=(",", ":"), ensure_ascii=False)
if hashlib.sha256(canonical.encode()).hexdigest() != fixture.get("payload_sha256"):
    fail("configuration fixture hash mismatch")
serialized = fixture_path.read_text().lower()
for forbidden in ("password", "secret", "api_key", "credential", "tx_arm", "transmit_enabled"):
    if forbidden in serialized:
        fail(f"configuration fixture contains forbidden key {forbidden}")

health = (ROOT / "android/app/src/main/java/app/rigweave/mobile/SystemHealthCentre.kt").read_text()
for forbidden in ("message_body", "qso_payload", "credential_value"):
    if forbidden in health.lower():
        fail(f"support-bundle implementation contains forbidden payload marker {forbidden}")

print("release-candidate audit PASS: docs, schemas, golden configuration, privacy contracts")
