#!/usr/bin/env python3
"""Fail-closed deployment, backup, restore, and smoke operations."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence


EVIDENCE_SCHEMA_VERSION = 1
MANIFEST_SCHEMA_VERSION = 1
COUNT_TABLES = (
    "auth_roles",
    "auth_role_permissions",
    "auth_users",
    "auth_user_roles",
    "auth_sessions",
    "auth_audit_events",
    "tenants",
    "tenant_memberships",
    "tenant_membership_roles",
    "tenant_model_configs",
    "import_batches",
    "dealers",
    "dealer_targets",
    "opportunities",
    "leads",
    "dealer_tasks",
    "campaigns",
    "organization_nodes",
    "organization_dealer_mappings",
    "organization_user_grants",
    "organization_role_grants",
    "knowledge_vector_store",
    "report_drafts",
    "report_subscriptions",
    "report_subscription_recipients",
    "report_generation_jobs",
    "tenant_smtp_configs",
    "report_deliveries",
    "report_collaborations",
    "report_collaboration_events",
    "report_collaboration_notifications",
    "model_price_versions",
    "model_budget_policies",
    "model_budget_reservations",
    "model_usage_events",
)
REQUIRED_TABLES = ("flyway_schema_history", *COUNT_TABLES)


class OperationError(RuntimeError):
    """An expected operational failure with a safe public message."""


@dataclass
class Evidence:
    operation: str
    started_at: str = field(default_factory=lambda: utc_now())
    checks: list[dict[str, Any]] = field(default_factory=list)
    artifacts: dict[str, Any] = field(default_factory=dict)
    status: str = "running"
    completed_at: str | None = None

    def check(self, name: str, status: str, detail: Any | None = None) -> None:
        item: dict[str, Any] = {"name": name, "status": status}
        if detail is not None:
            item["detail"] = detail
        self.checks.append(item)

    def finish(self, status: str) -> None:
        self.status = status
        self.completed_at = utc_now()

    def as_dict(self) -> dict[str, Any]:
        return {
            "schemaVersion": EVIDENCE_SCHEMA_VERSION,
            "operation": self.operation,
            "status": self.status,
            "startedAt": self.started_at,
            "completedAt": self.completed_at,
            "checks": self.checks,
            "artifacts": self.artifacts,
        }


class CommandRunner:
    def run(
        self,
        args: Sequence[str],
        *,
        environment: Mapping[str, str] | None = None,
    ) -> str:
        completed = subprocess.run(
            list(args),
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            env=dict(environment) if environment is not None else None,
        )
        if completed.returncode != 0:
            raise OperationError(f"Command failed: {Path(args[0]).name}")
        return completed.stdout.strip()


class DatabaseClient:
    def __init__(self, runner: CommandRunner, environment: Mapping[str, str]) -> None:
        self.runner = runner
        self.environment = dict(environment)

    def with_database(self, database: str) -> "DatabaseClient":
        updated = dict(self.environment)
        updated["PGDATABASE"] = database
        return DatabaseClient(self.runner, updated)

    def value(self, sql: str) -> str:
        output = self.runner.run(
            (
                "psql",
                "-X",
                "--no-psqlrc",
                "--quiet",
                "--tuples-only",
                "--no-align",
                "--set=ON_ERROR_STOP=1",
                "--command",
                sql,
            ),
            environment=self.environment,
        )
        lines = [line.strip() for line in output.splitlines() if line.strip()]
        if len(lines) != 1:
            raise OperationError("Database check returned an unexpected result shape")
        return lines[0]

    def state(self) -> dict[str, Any]:
        missing = [table for table in REQUIRED_TABLES if self.value(
            f"SELECT CASE WHEN to_regclass('public.{table}') IS NULL THEN 'missing' ELSE 'present' END;"
        ) != "present"]
        if missing:
            return {
                "migrationVersion": "unknown",
                "failedMigrations": -1,
                "enabledTenants": 0,
                "enabledMemberships": 0,
                "activeBatches": 0,
                "activeBatchConflicts": -1,
                "missingTables": missing,
                "recordCounts": {},
            }
        state: dict[str, Any] = {
            "migrationVersion": self.value(
                "SELECT COALESCE((SELECT version FROM flyway_schema_history "
                "WHERE success AND version IS NOT NULL "
                "ORDER BY installed_rank DESC LIMIT 1), 'none');"
            ),
            "failedMigrations": int(self.value(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success;"
            )),
            "enabledTenants": int(self.value("SELECT COUNT(*) FROM tenants WHERE enabled;")),
            "enabledMemberships": int(self.value(
                "SELECT COUNT(*) FROM tenant_memberships membership "
                "JOIN tenants tenant ON tenant.id = membership.tenant_id "
                "JOIN auth_users auth_user ON auth_user.id = membership.user_id "
                "WHERE membership.enabled AND tenant.enabled AND auth_user.enabled;"
            )),
            "activeBatches": int(self.value(
                "SELECT COUNT(*) FROM import_batches WHERE active;"
            )),
            "activeBatchConflicts": int(self.value(
                "SELECT COUNT(*) FROM (SELECT tenant_id FROM import_batches WHERE active "
                "GROUP BY tenant_id HAVING COUNT(*) > 1) conflicts;"
            )),
            "missingTables": missing,
            "recordCounts": {},
        }
        counts = state["recordCounts"]
        for table in COUNT_TABLES:
            counts[table] = int(self.value(f"SELECT COUNT(*) FROM {table};"))
        return state

    def user_table_count(self) -> int:
        return int(self.value(
            "SELECT COUNT(*) FROM pg_catalog.pg_tables "
            "WHERE schemaname NOT IN ('pg_catalog', 'information_schema');"
        ))


class HttpTransport:
    def request(
        self,
        base_url: str,
        path: str,
        *,
        method: str = "GET",
        headers: Mapping[str, str] | None = None,
        payload: Mapping[str, Any] | None = None,
        timeout: float = 30.0,
    ) -> tuple[int, Mapping[str, str], bytes]:
        body = None if payload is None else json.dumps(payload).encode("utf-8")
        request_headers = {
            "Accept": "application/json",
            "X-Request-ID": f"deployment-smoke-{uuid.uuid4().hex}",
        }
        if body is not None:
            request_headers["Content-Type"] = "application/json"
        if headers:
            request_headers.update(headers)
        request = urllib.request.Request(
            urllib.parse.urljoin(base_url + "/", path.lstrip("/")),
            data=body,
            headers=request_headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return response.status, response.headers, response.read()
        except urllib.error.HTTPError as exception:
            exception.read()
            return exception.code, exception.headers, b""
        except (urllib.error.URLError, TimeoutError) as exception:
            raise OperationError(f"HTTP request failed for {path}") from exception


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def require_new_file(path: Path, label: str) -> None:
    if path.exists():
        raise OperationError(f"{label} already exists; choose a new path")
    path.parent.mkdir(parents=True, exist_ok=True)


def write_json(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    content = json.dumps(value, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
    try:
        with path.open("x", encoding="utf-8", newline="\n") as destination:
            destination.write(content)
    except FileExistsError as exception:
        raise OperationError("Evidence or manifest file already exists; choose a new path") from exception


def require_postgres_environment(environment: Mapping[str, str]) -> dict[str, str]:
    required = ("PGHOST", "PGPORT", "PGUSER", "PGDATABASE")
    missing = [name for name in required if not environment.get(name, "").strip()]
    if not environment.get("PGPASSWORD", "").strip() and not environment.get("PGPASSFILE", "").strip():
        missing.append("PGPASSWORD or PGPASSFILE")
    if missing:
        raise OperationError("Missing PostgreSQL environment: " + ", ".join(missing))
    return dict(environment)


def require_tools(runner: CommandRunner, names: Sequence[str], evidence: Evidence) -> None:
    for name in names:
        if shutil.which(name) is None:
            raise OperationError(f"Required command is unavailable: {name}")
        output = runner.run((name, "--version"))
        version = output.splitlines()[0][:160] if output else "available"
        evidence.check(f"tool.{name}", "passed", version)


def validate_database_state(
    state: Mapping[str, Any],
    evidence: Evidence,
    expected_migration: str | None,
) -> None:
    checks = {
        "schema.required_tables": not state["missingTables"],
        "schema.failed_migrations": state["failedMigrations"] == 0,
        "tenant.enabled": state["enabledTenants"] > 0,
        "tenant.membership": state["enabledMemberships"] > 0,
        "tenant.active_batch": state["activeBatches"] > 0,
        "tenant.active_batch_conflicts": state["activeBatchConflicts"] == 0,
    }
    if expected_migration is not None:
        checks["schema.expected_migration"] = state["migrationVersion"] == expected_migration
    for name, passed in checks.items():
        evidence.check(name, "passed" if passed else "failed")
    if not all(checks.values()):
        raise OperationError("Database schema or tenant consistency check failed")
    evidence.artifacts["databaseState"] = state


def inspect_archive(
    runner: CommandRunner,
    archive: Path,
    environment: Mapping[str, str],
    evidence: Evidence,
) -> None:
    if not archive.is_file() or archive.stat().st_size == 0:
        raise OperationError("Backup archive is missing or empty")
    runner.run(("pg_restore", "--list", str(archive)), environment=environment)
    evidence.check("backup.archive_list", "passed")


def read_manifest(manifest_path: Path) -> dict[str, Any]:
    if not manifest_path.is_file() or manifest_path.stat().st_size > 1024 * 1024:
        raise OperationError("Backup manifest is missing or too large")
    try:
        value = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise OperationError("Backup manifest is not valid JSON") from exception
    if not isinstance(value, dict) or value.get("schemaVersion") != MANIFEST_SCHEMA_VERSION:
        raise OperationError("Backup manifest schema is unsupported")
    return value


def verify_manifest(archive: Path, manifest_path: Path, evidence: Evidence) -> dict[str, Any]:
    manifest = read_manifest(manifest_path)
    if manifest.get("archive", {}).get("fileName") != archive.name:
        raise OperationError("Backup manifest does not match the archive name")
    actual_hash = sha256_file(archive)
    if manifest.get("archive", {}).get("sha256") != actual_hash:
        raise OperationError("Backup archive checksum does not match the manifest")
    if manifest.get("archive", {}).get("sizeBytes") != archive.stat().st_size:
        raise OperationError("Backup archive size does not match the manifest")
    evidence.check("backup.manifest", "passed")
    return manifest


def validate_restored_counts(
    state: Mapping[str, Any],
    manifest: Mapping[str, Any],
    evidence: Evidence,
) -> None:
    expected = manifest.get("recordCounts")
    actual = state.get("recordCounts")
    if not isinstance(expected, dict) or not isinstance(actual, dict):
        raise OperationError("Backup manifest record counts are missing")
    invalid = [
        table for table in COUNT_TABLES
        if not isinstance(expected.get(table), int) or expected[table] < 0
    ]
    if invalid:
        raise OperationError("Backup manifest record counts are invalid")
    mismatched = [table for table in COUNT_TABLES if actual.get(table) != expected[table]]
    evidence.check("restore.record_counts", "failed" if mismatched else "passed")
    if mismatched:
        raise OperationError("Restored database record counts do not match the backup manifest")


def run_preflight(args: argparse.Namespace, evidence: Evidence, runner: CommandRunner) -> None:
    environment = require_postgres_environment(os.environ)
    tools = ["psql"]
    if args.archive:
        tools.append("pg_restore")
    require_tools(runner, tools, evidence)
    client = DatabaseClient(runner, environment)
    validate_database_state(client.state(), evidence, args.expected_migration)
    if args.archive:
        archive = Path(args.archive)
        inspect_archive(runner, archive, environment, evidence)
        verify_manifest(archive, Path(args.manifest), evidence)


def run_backup(args: argparse.Namespace, evidence: Evidence, runner: CommandRunner) -> None:
    require_safe_identifier(args.application_version, "Application version", 128)
    environment = require_postgres_environment(os.environ)
    require_tools(runner, ("psql", "pg_dump", "pg_restore"), evidence)
    output = Path(args.output)
    manifest_path = Path(str(output) + ".manifest.json")
    require_new_file(output, "Backup archive")
    if manifest_path.exists():
        raise OperationError("Backup manifest already exists; choose a new archive path")
    client = DatabaseClient(runner, environment)
    state = client.state()
    validate_database_state(state, evidence, None)
    partial = output.with_name(output.name + ".partial")
    if partial.exists():
        raise OperationError("A partial backup already exists; inspect it before retrying")
    try:
        runner.run(
            (
                "pg_dump",
                "--format=custom",
                "--compress=9",
                "--no-owner",
                "--no-privileges",
                "--file",
                str(partial),
            ),
            environment=environment,
        )
        inspect_archive(runner, partial, environment, evidence)
        partial.replace(output)
    except Exception:
        partial.unlink(missing_ok=True)
        raise
    manifest = {
        "schemaVersion": MANIFEST_SCHEMA_VERSION,
        "createdAt": utc_now(),
        "applicationVersion": args.application_version,
        "migrationVersion": state["migrationVersion"],
        "archive": {
            "fileName": output.name,
            "format": "postgresql-custom",
            "sizeBytes": output.stat().st_size,
            "sha256": sha256_file(output),
        },
        "recordCounts": state["recordCounts"],
    }
    write_json(manifest_path, manifest)
    evidence.check("backup.completed", "passed")
    evidence.artifacts.update({
        "archive": output.name,
        "manifest": manifest_path.name,
        "sha256": manifest["archive"]["sha256"],
    })


def run_restore(args: argparse.Namespace, evidence: Evidence, runner: CommandRunner) -> None:
    require_safe_database_name(args.target_database)
    if args.confirm_target != args.target_database:
        raise OperationError("Restore confirmation must exactly match the target database name")
    environment = require_postgres_environment(os.environ)
    require_tools(runner, ("psql", "pg_restore"), evidence)
    archive = Path(args.archive)
    manifest_path = Path(args.manifest)
    inspect_archive(runner, archive, environment, evidence)
    manifest = verify_manifest(archive, manifest_path, evidence)
    target = DatabaseClient(runner, environment).with_database(args.target_database)
    if target.user_table_count() != 0:
        raise OperationError("Restore target is not an empty database")
    evidence.check("restore.empty_target", "passed")
    runner.run(
        (
            "pg_restore",
            "--exit-on-error",
            "--single-transaction",
            "--no-owner",
            "--no-privileges",
            "--dbname",
            args.target_database,
            str(archive),
        ),
        environment=environment,
    )
    restored_state = target.state()
    validate_database_state(restored_state, evidence, args.expected_migration)
    validate_restored_counts(restored_state, manifest, evidence)
    evidence.check("restore.completed", "passed")
    evidence.artifacts.update({
        "archive": archive.name,
        "manifestCreatedAt": manifest.get("createdAt"),
        "targetDatabase": args.target_database,
    })


def parse_json_response(status: int, body: bytes, path: str) -> dict[str, Any]:
    if status != 200:
        raise OperationError(f"HTTP check failed for {path}: status {status}")
    try:
        parsed = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise OperationError(f"HTTP check returned invalid JSON for {path}") from exception
    if not isinstance(parsed, dict):
        raise OperationError(f"HTTP check returned an invalid envelope for {path}")
    return parsed


def require_success_envelope(status: int, body: bytes, path: str) -> dict[str, Any]:
    parsed = parse_json_response(status, body, path)
    if parsed.get("code") != 200 or "data" not in parsed:
        raise OperationError(f"HTTP check returned an unsuccessful envelope for {path}")
    return parsed


def validate_base_url(base_url: str, allow_http: bool) -> str:
    parsed = urllib.parse.urlparse(base_url)
    is_origin = (
        bool(parsed.netloc)
        and parsed.username is None
        and parsed.password is None
        and parsed.path in ("", "/")
        and not parsed.params
        and not parsed.query
        and not parsed.fragment
    )
    if parsed.scheme == "https" and is_origin:
        return base_url.rstrip("/")
    if (allow_http
            and parsed.scheme == "http"
            and is_origin
            and parsed.hostname in {"127.0.0.1", "localhost", "::1"}):
        return base_url.rstrip("/")
    raise OperationError("Smoke base URL must use HTTPS, except explicitly allowed loopback HTTP")


def checked_json(
    transport: HttpTransport,
    evidence: Evidence,
    base_url: str,
    path: str,
    *,
    headers: Mapping[str, str] | None = None,
    method: str = "GET",
    payload: Mapping[str, Any] | None = None,
    timeout: float,
) -> dict[str, Any]:
    started = time.monotonic()
    status, _, body = transport.request(
        base_url,
        path,
        method=method,
        headers=headers,
        payload=payload,
        timeout=timeout,
    )
    parsed = require_success_envelope(status, body, path)
    evidence.check(path, "passed", {"durationMs": round((time.monotonic() - started) * 1000, 1)})
    return parsed


def run_smoke(
    args: argparse.Namespace,
    evidence: Evidence,
    transport: HttpTransport,
    environment: Mapping[str, str],
) -> None:
    base_url = validate_base_url(args.base_url, args.allow_http)
    username = environment.get("SMOKE_USERNAME", "").strip()
    password = environment.get("SMOKE_PASSWORD", "")
    if not username or not password:
        raise OperationError("SMOKE_USERNAME and SMOKE_PASSWORD are required")
    for path in ("/livez", "/readyz"):
        started = time.monotonic()
        status, _, body = transport.request(base_url, path, timeout=args.timeout)
        health = parse_json_response(status, body, path)
        if health.get("status") != "UP":
            raise OperationError(f"Health check is not UP for {path}")
        evidence.check(path, "passed", {"durationMs": round((time.monotonic() - started) * 1000, 1)})
    login = checked_json(
        transport,
        evidence,
        base_url,
        "/api/auth/login",
        method="POST",
        payload={"username": username, "password": password},
        timeout=args.timeout,
    )
    session = login.get("data") or {}
    token = session.get("accessToken", "")
    user = session.get("user") or {}
    if not token or user.get("mustChangePassword") is True:
        raise OperationError("Smoke principal is not ready for authenticated checks")
    current_tenant = user.get("currentTenant") or {}
    tenant_key = args.tenant_key or current_tenant.get("key")
    if not tenant_key:
        tenants = user.get("tenants") or []
        if len(tenants) == 1:
            tenant_key = tenants[0].get("key")
    if not tenant_key:
        raise OperationError("Smoke tenant is ambiguous; set --tenant-key")
    auth_headers = {
        "Authorization": f"Bearer {token}",
        "X-Tenant-Key": tenant_key,
    }
    current_identity = checked_json(
        transport,
        evidence,
        base_url,
        "/api/auth/me",
        headers=auth_headers,
        timeout=args.timeout,
    )
    selected_tenant = ((current_identity.get("data") or {}).get("currentTenant") or {}).get("key")
    if selected_tenant != tenant_key:
        raise OperationError("Authenticated smoke did not select the requested tenant")
    read_paths = (
        "/api/dashboard",
        "/api/data-status",
        "/api/admin/organizations/nodes",
        "/api/reports/drafts",
        "/api/report-subscriptions",
        "/api/report-jobs",
        "/api/report-deliveries",
        "/api/admin/model-usage/summary",
    )
    for path in read_paths:
        checked_json(
            transport,
            evidence,
            base_url,
            path,
            headers=auth_headers,
            timeout=args.timeout,
        )
    status, response_headers, body = transport.request(
        base_url,
        "/api/chat/stream",
        method="POST",
        headers={**auth_headers, "Accept": "text/event-stream"},
        payload={
            "sessionId": f"deployment-smoke-{uuid.uuid4().hex}",
            "message": "What is the target achievement definition?",
        },
        timeout=args.timeout,
    )
    content_type = response_headers.get("Content-Type", "")
    stream = body.decode("utf-8", errors="replace")
    if status != 200 or not content_type.startswith("text/event-stream"):
        raise OperationError("SSE smoke check did not return an event stream")
    if "event: done" not in stream or "event: error" in stream:
        raise OperationError("SSE smoke check did not finish successfully")
    evidence.check("/api/chat/stream", "passed")
    if args.allow_writes:
        report = checked_json(
            transport,
            evidence,
            base_url,
            "/api/reports/drafts",
            headers=auth_headers,
            method="POST",
            payload={"reportType": "daily", "language": "en"},
            timeout=args.timeout,
        )
        report_id = (report.get("data") or {}).get("id")
        if not isinstance(report_id, str) or not re.fullmatch(r"[A-Za-z0-9._:-]{1,128}", report_id):
            raise OperationError("Report write smoke did not return a resource ID")
        evidence.artifacts["createdReportDraftId"] = report_id


def run_bootstrap_password(
    args: argparse.Namespace,
    evidence: Evidence,
    transport: HttpTransport,
    environment: Mapping[str, str],
) -> None:
    base_url = validate_base_url(args.base_url, args.allow_http)
    username = environment.get("BOOTSTRAP_USERNAME", "").strip()
    current_password = environment.get("BOOTSTRAP_CURRENT_PASSWORD", "")
    new_password = environment.get("BOOTSTRAP_NEW_PASSWORD", "")
    if not username or not current_password or not new_password:
        raise OperationError(
            "BOOTSTRAP_USERNAME, BOOTSTRAP_CURRENT_PASSWORD, and BOOTSTRAP_NEW_PASSWORD are required")
    login = checked_json(
        transport,
        evidence,
        base_url,
        "/api/auth/login",
        method="POST",
        payload={"username": username, "password": current_password},
        timeout=args.timeout,
    )
    session = login.get("data") or {}
    token = session.get("accessToken", "")
    user = session.get("user") or {}
    if not token or user.get("mustChangePassword") is not True:
        raise OperationError("Bootstrap principal is not awaiting a forced password change")
    status, _, body = transport.request(
        base_url,
        "/api/auth/password",
        method="POST",
        headers={"Authorization": f"Bearer {token}"},
        payload={"currentPassword": current_password, "newPassword": new_password},
        timeout=args.timeout,
    )
    require_success_envelope(status, body, "/api/auth/password")
    evidence.check("/api/auth/password", "passed")


def require_safe_identifier(value: str, label: str, maximum: int) -> None:
    if not re.fullmatch(rf"[A-Za-z0-9][A-Za-z0-9._-]{{0,{maximum - 1}}}", value):
        raise OperationError(f"{label} contains unsupported characters")


def require_safe_database_name(value: str) -> None:
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_-]{0,62}", value):
        raise OperationError("Restore target must be a plain PostgreSQL database name")


def add_evidence_argument(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--evidence", required=True, help="New JSON evidence output path")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="operation", required=True)

    preflight = subparsers.add_parser("preflight", help="Validate tools, schema, tenant state, and backup")
    add_evidence_argument(preflight)
    preflight.add_argument("--expected-migration", default="12")
    preflight.add_argument("--archive")
    preflight.add_argument("--manifest")

    backup = subparsers.add_parser("backup", help="Create and verify a custom-format backup")
    add_evidence_argument(backup)
    backup.add_argument("--output", required=True)
    backup.add_argument("--application-version", required=True)

    restore = subparsers.add_parser("restore", help="Restore into a confirmed empty database")
    add_evidence_argument(restore)
    restore.add_argument("--archive", required=True)
    restore.add_argument("--manifest", required=True)
    restore.add_argument("--target-database", required=True)
    restore.add_argument("--confirm-target", required=True)
    restore.add_argument("--expected-migration", default="12")

    smoke = subparsers.add_parser("smoke", help="Run health and authenticated application smoke checks")
    add_evidence_argument(smoke)
    smoke.add_argument("--base-url", required=True)
    smoke.add_argument("--tenant-key")
    smoke.add_argument("--timeout", type=float, default=60.0)
    smoke.add_argument("--allow-http", action="store_true")
    smoke.add_argument("--allow-writes", action="store_true")

    bootstrap_password = subparsers.add_parser(
        "bootstrap-password",
        help="Replace a one-time administrator bootstrap password",
    )
    add_evidence_argument(bootstrap_password)
    bootstrap_password.add_argument("--base-url", required=True)
    bootstrap_password.add_argument("--timeout", type=float, default=60.0)
    bootstrap_password.add_argument("--allow-http", action="store_true")
    return parser


def validate_arguments(args: argparse.Namespace) -> None:
    if args.operation == "preflight" and bool(args.archive) != bool(args.manifest):
        raise OperationError("Preflight archive and manifest must be provided together")
    evidence_path = Path(args.evidence)
    if evidence_path.exists():
        raise OperationError("Evidence file already exists; choose a new path")


def execute(args: argparse.Namespace) -> tuple[int, Evidence]:
    evidence = Evidence(args.operation)
    runner = CommandRunner()
    operations: dict[str, Callable[[], None]] = {
        "preflight": lambda: run_preflight(args, evidence, runner),
        "backup": lambda: run_backup(args, evidence, runner),
        "restore": lambda: run_restore(args, evidence, runner),
        "smoke": lambda: run_smoke(args, evidence, HttpTransport(), os.environ),
        "bootstrap-password": lambda: run_bootstrap_password(
            args, evidence, HttpTransport(), os.environ),
    }
    try:
        validate_arguments(args)
        operations[args.operation]()
        evidence.finish("passed")
        return 0, evidence
    except OperationError as exception:
        evidence.check("operation", "failed", str(exception))
        evidence.finish("failed")
        return 1, evidence
    except Exception as exception:
        evidence.check("operation", "failed", f"Unexpected failure: {type(exception).__name__}")
        evidence.finish("failed")
        return 1, evidence


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    exit_code, evidence = execute(args)
    evidence_path = Path(args.evidence)
    try:
        write_json(evidence_path, evidence.as_dict())
    except OperationError as exception:
        print(str(exception), file=sys.stderr)
        return 1
    print(json.dumps({
        "operation": evidence.operation,
        "status": evidence.status,
        "evidence": str(evidence_path),
    }, ensure_ascii=True))
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
