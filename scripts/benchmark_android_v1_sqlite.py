#!/usr/bin/env python3
"""Reproduce Android 1.0 projection-query timings on a private database copy.

The script never prints row values. It copies the source through SQLite's backup
API, applies the Android 1.0 projection migration to that copy, and emits only
aggregate counts, query plans, and timing statistics.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import platform
import sqlite3
import statistics
import time
from pathlib import Path
from typing import Any, Iterable


PROJECTION_INDEXES = (
    "CREATE INDEX IF NOT EXISTS qso_projection_time_idx ON qso_projection(created_at DESC,qso_id)",
    "CREATE INDEX IF NOT EXISTS qso_projection_call_idx ON qso_projection(callsign_norm,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_frequency_idx ON qso_projection(frequency_hz,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_band_time_idx ON qso_projection(band_norm,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_mode_time_idx ON qso_projection(mode_family,submode_norm,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_station_time_idx ON qso_projection(station_profile_id,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_station_callsign_idx ON qso_projection(station_profile_id,callsign_norm)",
    "CREATE INDEX IF NOT EXISTS qso_projection_station_call_idx ON qso_projection(station_callsign_norm,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_operator_idx ON qso_projection(operator_norm,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_dxcc_bm_idx ON qso_projection(dxcc,band_norm,mode_family,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_country_idx ON qso_projection(country_norm,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_geo_idx ON qso_projection(continent,cq_zone,itu_zone,state_norm)",
    "CREATE INDEX IF NOT EXISTS qso_projection_grid_idx ON qso_projection(grid_norm,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_contest_idx ON qso_projection(contest_id_norm,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_satellite_idx ON qso_projection(satellite_name,satellite_mode,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_activation_idx ON qso_projection(activation_program,activation_session_id,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_sync_idx ON qso_projection(sync_state,remote_id,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_confirmation_idx ON qso_projection(paper_received,lotw_received,created_at DESC)",
    "CREATE INDEX IF NOT EXISTS qso_projection_distance_idx ON qso_projection(distance_km DESC) WHERE distance_km>0",
    "CREATE INDEX IF NOT EXISTS qso_projection_day_idx ON qso_projection(utc_day)",
    "CREATE INDEX IF NOT EXISTS qso_projection_month_idx ON qso_projection(utc_month)",
    "CREATE INDEX IF NOT EXISTS qso_reference_program_idx ON qso_reference(program,direction,reference_norm,qso_id)",
    "CREATE INDEX IF NOT EXISTS qso_reference_qso_idx ON qso_reference(qso_id,program,direction)",
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def percentile(values: list[float], percent: float) -> float:
    return sorted(values)[max(0, math.ceil(percent * len(values)) - 1)]


def benchmark(
    connection: sqlite3.Connection,
    sql: str,
    args: Iterable[Any],
    iterations: int,
    warmups: int,
) -> dict[str, Any]:
    parameters = tuple(args)
    for _ in range(warmups):
        connection.execute(sql, parameters).fetchall()
    samples: list[float] = []
    row_count = 0
    for _ in range(iterations):
        started = time.perf_counter_ns()
        rows = connection.execute(sql, parameters).fetchall()
        samples.append((time.perf_counter_ns() - started) / 1_000_000.0)
        row_count = len(rows)
    plan = [row[3] for row in connection.execute("EXPLAIN QUERY PLAN " + sql, parameters)]
    return {
        "iterations": iterations,
        "warmups": warmups,
        "rows": row_count,
        "median_ms": round(statistics.median(samples), 3),
        "p95_ms": round(percentile(samples, 0.95), 3),
        "minimum_ms": round(min(samples), 3),
        "maximum_ms": round(max(samples), 3),
        "query_plan": plan,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--work-db", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--iterations", type=int, default=75)
    parser.add_argument("--warmups", type=int, default=10)
    options = parser.parse_args()
    if options.iterations < 10 or options.warmups < 1:
        parser.error("iterations must be >= 10 and warmups must be >= 1")
    if not options.source.is_file():
        parser.error("source database does not exist")
    if options.source.resolve() == options.work_db.resolve():
        parser.error("work database must not be the source database")
    if options.work_db.exists():
        parser.error("work database already exists; choose a fresh path")
    if options.output.exists():
        parser.error("output already exists; choose a fresh path")

    options.work_db.parent.mkdir(parents=True, exist_ok=True)
    options.output.parent.mkdir(parents=True, exist_ok=True)

    source_uri = f"file:{options.source.resolve()}?mode=ro"
    with sqlite3.connect(source_uri, uri=True) as source, sqlite3.connect(options.work_db) as target:
        source.backup(target)

    migration_started = time.perf_counter_ns()
    with sqlite3.connect(options.work_db) as connection:
        columns = {row[1] for row in connection.execute("PRAGMA table_info(qso_projection)")}
        if "country_display" not in columns:
            connection.execute("ALTER TABLE qso_projection ADD COLUMN country_display TEXT NOT NULL DEFAULT ''")
            connection.execute(
                "UPDATE qso_projection SET country_display=COALESCE("
                "(SELECT q.country FROM qso q WHERE q.id=qso_projection.qso_id),'')"
            )
        for statement in PROJECTION_INDEXES:
            connection.execute(statement)
        connection.commit()
        migration_ms = (time.perf_counter_ns() - migration_started) / 1_000_000.0

        canonical_rows = connection.execute("SELECT COUNT(*) FROM qso").fetchone()[0]
        projection_rows = connection.execute("SELECT COUNT(*) FROM qso_projection").fetchone()[0]
        if canonical_rows != projection_rows:
            raise RuntimeError(f"projection mismatch: canonical={canonical_rows} projection={projection_rows}")

        station_id = connection.execute(
            "SELECT station_profile_id FROM qso_projection GROUP BY station_profile_id ORDER BY COUNT(*) DESC LIMIT 1"
        ).fetchone()[0]
        calls = [
            row[0]
            for row in connection.execute(
                "SELECT callsign_norm FROM qso_projection WHERE station_profile_id=? AND callsign_norm<>'' "
                "GROUP BY callsign_norm ORDER BY COUNT(*) DESC,callsign_norm LIMIT 25",
                (station_id,),
            )
        ]
        if not calls:
            raise RuntimeError("no benchmark callsigns found")
        placeholders = ",".join("?" for _ in calls)

        callsign_sql = f"""SELECT p.callsign_norm,p.band_norm,p.submode_norm,p.mode_norm,
            MAX(CASE WHEN p.paper_received=1 OR p.lotw_received=1 THEN 1 ELSE 0 END)
            FROM qso_projection p WHERE p.callsign_norm IN ({placeholders}) AND p.station_profile_id=?
            GROUP BY p.callsign_norm,p.band_norm,p.submode_norm,p.mode_norm"""
        worked_log_sql = """SELECT callsign_norm,country_display,frequency_hz,mode_norm,created_at,band_norm,submode_norm
            FROM qso_projection p WHERE p.station_profile_id=? ORDER BY created_at"""
        projection_page_sql = """SELECT p.qso_id,p.created_at FROM qso_projection p WHERE 1=1 AND p.station_profile_id=?
            ORDER BY p.created_at DESC,p.qso_id ASC LIMIT ?"""
        intelligence_sql = """SELECT COUNT(*),COUNT(DISTINCT NULLIF(callsign_norm,'')),
            COALESCE(SUM(paper_received),0),COALESCE(SUM(lotw_received),0),COALESCE(SUM(eqsl_received),0),
            COALESCE(SUM(qrz_received),0),COALESCE(SUM(clublog_received),0),COALESCE(SUM(dcl_received),0),
            COALESCE(SUM(dxcc<>''),0),COALESCE(SUM(cq_zone<>''),0),COALESCE(SUM(itu_zone<>''),0),
            COALESCE(SUM(state_norm<>''),0),COALESCE(SUM(grid_norm<>''),0),COALESCE(SUM(distance_km>0),0),
            COALESCE(SUM(tx_power_w>0),0),COALESCE(SUM(station_profile_id<>'' OR station_callsign_norm<>''),0),
            COALESCE(SUM(pota_ref_norm<>'' OR sota_ref_norm<>'' OR wwff_ref_norm<>'' OR iota_norm<>''),0),
            COUNT(DISTINCT CASE WHEN dxcc<>'' AND country_norm<>'' THEN dxcc END),COUNT(DISTINCT NULLIF(grid_norm,'')),MAX(distance_km),
            COALESCE(SUM(tx_power_w BETWEEN 1 AND 5),0),COUNT(DISTINCT utc_day),AVG(NULLIF(distance_km,0)),
            COUNT(DISTINCT CASE WHEN tx_power_w BETWEEN 1 AND 5 THEN NULLIF(dxcc,'') END),
            COUNT(DISTINCT CASE WHEN paper_received=1 OR lotw_received=1 THEN NULLIF(grid_norm,'') END)
            FROM qso_projection p WHERE 1=1 AND p.station_profile_id=? AND p.sync_state NOT IN ('TOMBSTONE','REMOTE_DELETED')"""

        results = {
            "schema": "rigweave-android-v1-sqlite-benchmark-v1",
            "generated_at_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "host": {"system": platform.system(), "machine": platform.machine(), "sqlite": sqlite3.sqlite_version},
            "privacy": "No row values or credentials are emitted.",
            "source": {
                "sha256": sha256(options.source),
                "bytes": options.source.stat().st_size,
                "canonical_rows": canonical_rows,
                "projection_rows": projection_rows,
            },
            "migration": {
                "android_v1_projection_migration_ms": round(migration_ms, 3),
                "country_display_present": True,
                "station_callsign_index_present": True,
            },
            "benchmarks": {
                "callsign_status_25_keys": benchmark(connection, callsign_sql, [*calls, station_id], options.iterations, options.warmups),
                "worked_log_projection": benchmark(connection, worked_log_sql, [station_id], options.iterations, options.warmups),
                "logbook_projection_page_51": benchmark(connection, projection_page_sql, [station_id, 51], options.iterations, options.warmups),
                "log_intelligence_aggregate": benchmark(connection, intelligence_sql, [station_id], options.iterations, options.warmups),
            },
            "device_only_pending": ["application_startup", "logbook_open_render", "intelligence_open_render"],
        }

    options.output.write_text(json.dumps(results, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({
        "output": str(options.output),
        "canonical_rows": results["source"]["canonical_rows"],
        "projection_rows": results["source"]["projection_rows"],
        "benchmarks": {name: {"median_ms": value["median_ms"], "p95_ms": value["p95_ms"], "rows": value["rows"]}
                       for name, value in results["benchmarks"].items()},
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
