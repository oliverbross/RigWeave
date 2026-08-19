#!/usr/bin/env python3
"""Audit pinned OpenHamClock stable and preview channels through the GitHub API."""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

API = "https://api.github.com"
ISSUE_TITLE = "OpenHamClock upstream audit requires review"
MAX_API_BYTES = 8_000_000
MAX_CHANGED_PATHS = 200
MAX_REPORT_BYTES = 500_000


class AuditError(RuntimeError):
    pass


class GitHub:
    def __init__(self, token: str = "") -> None:
        self.token = token

    def request(self, path: str, method: str = "GET", payload: dict[str, Any] | None = None) -> Any:
        url = path if path.startswith("https://") else API + path
        headers = {
            "Accept": "application/vnd.github+json",
            "User-Agent": "RigWeave-OpenHamClock-Upstream-Watch/1",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        data = json.dumps(payload).encode() if payload is not None else None
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                body = response.read(MAX_API_BYTES + 1)
                if len(body) > MAX_API_BYTES:
                    raise AuditError("GitHub response exceeded the audit byte limit")
                remaining = response.headers.get("X-RateLimit-Remaining")
                if remaining == "0":
                    raise AuditError("GitHub rate limit exhausted")
        except urllib.error.HTTPError as error:
            detail = error.read(2048).decode("utf-8", "replace")
            rate = error.headers.get("X-RateLimit-Remaining")
            if error.code in (403, 429) or rate == "0":
                raise AuditError(f"GitHub API rate-limited the audit (HTTP {error.code})") from error
            raise AuditError(f"GitHub API HTTP {error.code}: {detail[:300]}") from error
        except urllib.error.URLError as error:
            raise AuditError(f"GitHub API unavailable: {error.reason}") from error
        try:
            return json.loads(body)
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise AuditError("GitHub returned malformed JSON") from error

    def content(self, repo: str, path: str, ref: str) -> bytes:
        encoded_path = "/".join(urllib.parse.quote(part, safe="") for part in path.split("/"))
        row = self.request(f"/repos/{repo}/contents/{encoded_path}?ref={urllib.parse.quote(ref, safe='')}")
        if not isinstance(row, dict) or row.get("encoding") != "base64" or not isinstance(row.get("content"), str):
            raise AuditError(f"Malformed GitHub content metadata for {path}@{ref}")
        try:
            return base64.b64decode(row["content"], validate=False)
        except (ValueError, TypeError) as error:
            raise AuditError(f"Malformed base64 content for {path}@{ref}") from error


def require_sha(value: Any, label: str) -> str:
    if not isinstance(value, str) or re.fullmatch(r"[0-9a-f]{40}", value) is None:
        raise AuditError(f"Malformed {label} SHA")
    return value


def branch_sha(client: GitHub, repo: str, branch: str) -> str:
    row = client.request(f"/repos/{repo}/branches/{urllib.parse.quote(branch, safe='')}")
    if not isinstance(row, dict) or not isinstance(row.get("commit"), dict):
        raise AuditError(f"Malformed branch metadata for {branch}")
    return require_sha(row["commit"].get("sha"), branch)


def package_version(client: GitHub, repo: str, ref: str) -> str:
    try:
        row = json.loads(client.content(repo, "package.json", ref))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise AuditError(f"Malformed package.json at {ref}") from error
    version = row.get("version") if isinstance(row, dict) else None
    if not isinstance(version, str) or not version.strip() or len(version) > 80:
        raise AuditError(f"Malformed package version at {ref}")
    return version.strip()


def licence_state(client: GitHub, repo: str, ref: str) -> dict[str, str]:
    body = client.content(repo, "LICENSE", ref)
    if len(body) > 200_000:
        raise AuditError("Upstream LICENSE exceeded the audit byte limit")
    text = body.decode("utf-8", "strict")
    notice = next((line.strip() for line in text.splitlines() if line.lower().startswith("copyright")), "")
    if "MIT License" not in text or not notice:
        raise AuditError("Upstream LICENSE is not the expected MIT licence text")
    return {"identifier": "MIT", "sha256": hashlib.sha256(body).hexdigest(), "copyright_notice": notice}


def compare(client: GitHub, repo: str, base: str, head: str) -> dict[str, Any]:
    row = client.request(f"/repos/{repo}/compare/{base}...{head}")
    if not isinstance(row, dict) or row.get("status") not in {"ahead", "behind", "diverged", "identical"}:
        raise AuditError(f"Malformed comparison metadata for {base}...{head}")
    merge = row.get("merge_base_commit")
    files = row.get("files", [])
    if not isinstance(merge, dict) or not isinstance(files, list):
        raise AuditError(f"Incomplete comparison metadata for {base}...{head}")
    paths = []
    for item in files:
        name = item.get("filename") if isinstance(item, dict) else None
        if isinstance(name, str) and len(name) <= 500:
            paths.append(name)
    return {
        "status": row["status"],
        "ahead_by": int(row.get("ahead_by", 0)),
        "behind_by": int(row.get("behind_by", 0)),
        "merge_base": require_sha(merge.get("sha"), "merge-base"),
        "changed_paths": paths[:MAX_CHANGED_PATHS],
        "changed_paths_truncated": len(paths) > MAX_CHANGED_PATHS,
    }


def classify(paths: list[str], watched: dict[str, Any]) -> dict[str, list[str]]:
    result: dict[str, list[str]] = {}
    for concern, patterns in watched.items():
        if not isinstance(patterns, list):
            raise AuditError(f"Watched-path group {concern} is malformed")
        matches = []
        for path in paths:
            if any(isinstance(pattern, str) and (path == pattern or (pattern.endswith("/") and path.startswith(pattern))) for pattern in patterns):
                matches.append(path)
        if matches:
            result[concern] = matches[:MAX_CHANGED_PATHS]
    return result


def current_release(client: GitHub, repo: str) -> dict[str, str]:
    try:
        row = client.request(f"/repos/{repo}/releases/latest")
    except AuditError as error:
        if "HTTP 404" in str(error):
            return {"tag": "", "published_at": ""}
        raise
    if not isinstance(row, dict) or not isinstance(row.get("tag_name"), str):
        raise AuditError("Malformed latest-release metadata")
    return {"tag": row["tag_name"][:80], "published_at": str(row.get("published_at", ""))[:40]}


def channel_result(client: GitHub, repo: str, branch: str, pinned: str, watched: dict[str, Any]) -> dict[str, Any]:
    observed = branch_sha(client, repo, branch)
    result: dict[str, Any] = {"branch": branch, "pinned_sha": pinned, "observed_sha": observed, "changed": observed != pinned}
    if observed != pinned:
        result["comparison"] = compare(client, repo, pinned, observed)
        result["concerns"] = classify(result["comparison"]["changed_paths"], watched)
    else:
        result["comparison"] = {"status": "identical", "ahead_by": 0, "behind_by": 0, "merge_base": pinned,
                                "changed_paths": [], "changed_paths_truncated": False}
        result["concerns"] = {}
    return result


def audit(client: GitHub, manifest: dict[str, Any]) -> dict[str, Any]:
    upstream = manifest.get("upstream", {})
    repo = f"{upstream.get('owner', '')}/{upstream.get('repository', '')}"
    if repo != "accius/openhamclock":
        raise AuditError("Manifest upstream identity must be accius/openhamclock")
    stable_pin = require_sha(manifest.get("stable", {}).get("sha"), "stable pin")
    preview_pin = require_sha(manifest.get("preview", {}).get("sha"), "preview pin")
    watched = manifest.get("watched_source_paths")
    if not isinstance(watched, dict):
        raise AuditError("Manifest watched_source_paths is malformed")
    stable = channel_result(client, repo, str(manifest["stable"]["branch"]), stable_pin, watched)
    preview = channel_result(client, repo, str(manifest["preview"]["branch"]), preview_pin, watched)
    stable_version = package_version(client, repo, stable["observed_sha"])
    licence = licence_state(client, repo, stable["observed_sha"])
    relationship = compare(client, repo, stable["observed_sha"], preview["observed_sha"])
    pinned_version = str(manifest["stable"].get("package_version", ""))
    pinned_licence = str(manifest.get("licence", {}).get("sha256", ""))
    stable["package_version"] = stable_version
    stable["package_version_changed"] = stable_version != pinned_version
    stable["licence"] = licence
    stable["licence_changed"] = licence["sha256"] != pinned_licence
    preview_sensitive = bool(set(preview["concerns"]) & {"security", "providers", "propagation_algorithms"})
    issue_required = bool(stable["changed"] or stable["package_version_changed"] or stable["licence_changed"] or
                          (preview["changed"] and preview_sensitive))
    return {
        "schema_version": 1,
        "checked_at": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat(),
        "upstream": repo,
        "stable": stable,
        "preview": preview,
        "stable_preview_relationship": relationship,
        "latest_release": current_release(client, repo),
        "issue_required": issue_required,
        "issue_reason": "stable change" if stable["changed"] or stable["package_version_changed"] or stable["licence_changed"]
                        else "preview security/provider/algorithm change" if preview["changed"] and preview_sensitive else "none",
        "automatic_source_or_pin_change": False,
    }


def markdown(report: dict[str, Any]) -> str:
    stable, preview, relationship = report["stable"], report["preview"], report["stable_preview_relationship"]
    lines = [
        "# OpenHamClock upstream audit",
        "",
        f"Checked: `{report['checked_at']}`",
        f"Upstream: `{report['upstream']}`",
        "",
        "## Stable",
        "",
        f"- `{stable['branch']}` pinned `{stable['pinned_sha']}`, observed `{stable['observed_sha']}`",
        f"- Change: **{'YES' if stable['changed'] else 'NO'}**; package `{stable['package_version']}`; licence `{stable['licence']['identifier']}`",
        f"- Package changed: **{stable['package_version_changed']}**; licence changed: **{stable['licence_changed']}**",
        "",
        "## Preview",
        "",
        f"- `{preview['branch']}` pinned `{preview['pinned_sha']}`, observed `{preview['observed_sha']}`",
        f"- Change: **{'YES' if preview['changed'] else 'NO'}**",
        f"- Relationship to stable: `{relationship['status']}`, ahead {relationship['ahead_by']}, behind {relationship['behind_by']}, merge base `{relationship['merge_base']}`",
        "",
        "## Watched changes",
        "",
    ]
    for name, channel in (("stable", stable), ("preview", preview)):
        lines.append(f"### {name.title()}")
        if not channel["comparison"]["changed_paths"]:
            lines.append("- No changed paths from the pin.")
        else:
            for concern, paths in sorted(channel["concerns"].items()):
                lines.append(f"- **{concern}**: " + ", ".join(f"`{path}`" for path in paths[:20]))
            unclassified = [path for path in channel["comparison"]["changed_paths"] if not any(path in rows for rows in channel["concerns"].values())]
            if unclassified:
                lines.append("- **other**: " + ", ".join(f"`{path}`" for path in unclassified[:20]))
        lines.append("")
    lines += [f"Issue required: **{report['issue_required']}** ({report['issue_reason']}).",
              "", "This audit never changes RigWeave source or pins automatically."]
    body = "\n".join(lines) + "\n"
    return body[:MAX_REPORT_BYTES]


def update_issue(client: GitHub, repository: str, body: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise AuditError("Malformed issue repository")
    issues = client.request(f"/repos/{repository}/issues?state=open&per_page=100")
    if not isinstance(issues, list):
        raise AuditError("Malformed issue-list response")
    existing = next((row for row in issues if isinstance(row, dict) and row.get("title") == ISSUE_TITLE and "pull_request" not in row), None)
    payload = {"title": ISSUE_TITLE, "body": body[:60_000]}
    if existing is not None:
        number = existing.get("number")
        if not isinstance(number, int):
            raise AuditError("Malformed existing issue metadata")
        result = client.request(f"/repos/{repository}/issues/{number}", "PATCH", payload)
    else:
        result = client.request(f"/repos/{repository}/issues", "POST", payload)
    if not isinstance(result, dict) or not isinstance(result.get("html_url"), str):
        raise AuditError("Malformed issue-write response")
    return result["html_url"]


def self_test() -> None:
    watched = {"security": ["SECURITY.md", "server/middleware/"], "providers": ["server/routes/"], "licence": ["LICENSE"]}
    assert require_sha("a" * 40, "test") == "a" * 40
    try:
        require_sha("bad", "test")
        raise AssertionError("malformed SHA accepted")
    except AuditError:
        pass
    classified = classify(["SECURITY.md", "server/routes/propagation.js", "README.md"], watched)
    assert set(classified) == {"security", "providers"}
    assert classify(["LICENSE"], watched) == {"licence": ["LICENSE"]}
    stable = {"changed": False, "package_version_changed": False, "licence_changed": False}
    preview = {"changed": True, "concerns": {"security": ["SECURITY.md"]}}
    assert not stable["changed"] and preview["changed"]
    assert bool(set(preview["concerns"]) & {"security", "providers", "propagation_algorithms"})
    assert len(list(range(MAX_CHANGED_PATHS + 10))[:MAX_CHANGED_PATHS]) == MAX_CHANGED_PATHS
    assert hashlib.sha256(b"old").hexdigest() != hashlib.sha256(b"new").hexdigest()
    assert "26.5.0" != "26.6.0"
    print("8 watcher self-tests passed")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=Path("docs/hamclock/upstream.json"))
    parser.add_argument("--json-out", type=Path, default=Path("build/reports/openhamclock-upstream.json"))
    parser.add_argument("--markdown-out", type=Path, default=Path("build/reports/openhamclock-upstream.md"))
    parser.add_argument("--update-issue", action="store_true")
    parser.add_argument("--issue-repository", default=os.environ.get("GITHUB_REPOSITORY", ""))
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    try:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        if not isinstance(manifest, dict):
            raise AuditError("Manifest root must be an object")
        client = GitHub(os.environ.get("GITHUB_TOKEN", ""))
        report = audit(client, manifest)
        rendered = markdown(report)
        encoded = json.dumps(report, indent=2, sort_keys=True) + "\n"
        if len(encoded.encode()) > MAX_REPORT_BYTES:
            raise AuditError("JSON report exceeded the audit byte limit")
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.markdown_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(encoded, encoding="utf-8")
        args.markdown_out.write_text(rendered, encoding="utf-8")
        issue_url = ""
        if args.update_issue and report["issue_required"]:
            if not args.issue_repository or not client.token:
                raise AuditError("Issue update requested without repository/token")
            issue_url = update_issue(client, args.issue_repository, rendered)
        print(json.dumps({"stable_changed": report["stable"]["changed"], "preview_changed": report["preview"]["changed"],
                          "relationship": report["stable_preview_relationship"]["status"], "issue_required": report["issue_required"],
                          "issue_url": issue_url}, sort_keys=True))
        return 0
    except (AuditError, OSError, json.JSONDecodeError) as error:
        print(f"OpenHamClock upstream audit failed: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
