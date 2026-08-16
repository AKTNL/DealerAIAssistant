import argparse
import json
import tempfile
import unittest
from pathlib import Path

from ops.deployment.operations import (
    DatabaseClient,
    Evidence,
    HttpTransport,
    OperationError,
    REQUIRED_TABLES,
    require_postgres_environment,
    run_backup,
    run_bootstrap_password,
    run_restore,
    run_smoke,
    validate_base_url,
    validate_database_state,
    validate_restored_counts,
    verify_manifest,
)


class FakeRunner:
    def __init__(self, values=None):
        self.values = list(values or [])
        self.calls = []

    def run(self, args, *, environment=None):
        self.calls.append((tuple(args), dict(environment or {})))
        if args[0] == "psql" and self.values:
            return str(self.values.pop(0))
        return "tool 1.0"


class FakeTransport(HttpTransport):
    def __init__(self, must_change_password=False):
        self.calls = []
        self.must_change_password = must_change_password

    def request(self, base_url, path, **kwargs):
        self.calls.append((path, kwargs))
        if path in ("/livez", "/readyz"):
            return 200, {"Content-Type": "application/json"}, b'{"status":"UP"}'
        if path == "/api/auth/login":
            return 200, {"Content-Type": "application/json"}, json.dumps({
                "code": 200,
                "data": {
                    "accessToken": "secret-access-token",
                    "user": {
                        "mustChangePassword": self.must_change_password,
                        "currentTenant": {"key": "default"},
                        "tenants": [{"key": "default"}],
                    },
                },
            }).encode("utf-8")
        if path == "/api/chat/stream":
            return 200, {"Content-Type": "text/event-stream;charset=UTF-8"}, b"event: done\ndata: [DONE]\n\n"
        if path == "/api/auth/me":
            return 200, {"Content-Type": "application/json"}, (
                b'{"code":200,"data":{"currentTenant":{"key":"default"}}}'
            )
        if path == "/api/reports/drafts":
            return 200, {"Content-Type": "application/json"}, b'{"code":200,"data":{"id":"report-1"}}'
        if path == "/api/auth/password":
            return 200, {"Content-Type": "application/json"}, b'{"code":200,"data":null}'
        return 200, {"Content-Type": "application/json"}, b'{"code":200,"data":{}}'


class OperationsTest(unittest.TestCase):
    def test_postgres_environment_accepts_password_file_without_leaking_it(self):
        environment = {
            "PGHOST": "db.example.com",
            "PGPORT": "5432",
            "PGUSER": "operator",
            "PGDATABASE": "agentpoc",
            "PGPASSFILE": "/run/secrets/pgpass",
        }

        resolved = require_postgres_environment(environment)

        self.assertEqual("/run/secrets/pgpass", resolved["PGPASSFILE"])

    def test_postgres_environment_rejects_missing_noninteractive_credentials(self):
        with self.assertRaisesRegex(OperationError, "PGPASSWORD or PGPASSFILE"):
            require_postgres_environment({
                "PGHOST": "db.example.com",
                "PGPORT": "5432",
                "PGUSER": "operator",
                "PGDATABASE": "agentpoc",
            })

    def test_manifest_verification_detects_archive_tampering(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory, "backup.dump")
            manifest = Path(directory, "backup.dump.manifest.json")
            archive.write_bytes(b"archive")
            manifest.write_text(json.dumps({
                "schemaVersion": 1,
                "archive": {
                    "fileName": archive.name,
                    "sizeBytes": archive.stat().st_size,
                    "sha256": "0" * 64,
                },
            }), encoding="utf-8")

            with self.assertRaisesRegex(OperationError, "checksum"):
                verify_manifest(archive, manifest, Evidence("preflight"))

    def test_restore_count_validation_detects_incomplete_recovery(self):
        expected = {table: 1 for table in REQUIRED_TABLES if table != "flyway_schema_history"}
        actual = dict(expected)
        actual["report_drafts"] = 0
        evidence = Evidence("restore")

        with self.assertRaisesRegex(OperationError, "do not match"):
            validate_restored_counts(
                {"recordCounts": actual},
                {"recordCounts": expected},
                evidence,
            )

        self.assertEqual("failed", evidence.checks[-1]["status"])

    def test_restore_requires_exact_target_confirmation_before_commands(self):
        args = argparse.Namespace(
            confirm_target="wrong",
            target_database="agentpoc_restore",
            archive="backup.dump",
            manifest="backup.dump.manifest.json",
            expected_migration="12",
        )
        runner = FakeRunner()

        with self.assertRaisesRegex(OperationError, "exactly match"):
            run_restore(args, Evidence("restore"), runner)

        self.assertEqual([], runner.calls)

    def test_restore_rejects_an_unsafe_database_name_before_commands(self):
        args = argparse.Namespace(
            confirm_target="postgres://unsafe",
            target_database="postgres://unsafe",
            archive="backup.dump",
            manifest="backup.dump.manifest.json",
            expected_migration="12",
        )
        runner = FakeRunner()

        with self.assertRaisesRegex(OperationError, "plain PostgreSQL database name"):
            run_restore(args, Evidence("restore"), runner)

        self.assertEqual([], runner.calls)

    def test_backup_rejects_an_unsafe_application_version_before_commands(self):
        args = argparse.Namespace(
            application_version="release value with spaces",
            output="backup.dump",
        )
        runner = FakeRunner()

        with self.assertRaisesRegex(OperationError, "unsupported characters"):
            run_backup(args, Evidence("backup"), runner)

        self.assertEqual([], runner.calls)

    def test_database_state_reports_missing_required_tables_without_extra_queries(self):
        runner = FakeRunner(["missing", *(["present"] * (len(REQUIRED_TABLES) - 1))])

        state = DatabaseClient(runner, {}).state()

        self.assertEqual([REQUIRED_TABLES[0]], state["missingTables"])
        self.assertEqual(len(REQUIRED_TABLES), len(runner.calls))

    def test_database_validation_requires_an_active_batch(self):
        evidence = Evidence("preflight")
        state = {
            "migrationVersion": "12",
            "failedMigrations": 0,
            "enabledTenants": 1,
            "enabledMemberships": 1,
            "activeBatches": 0,
            "activeBatchConflicts": 0,
            "missingTables": [],
            "recordCounts": {},
        }

        with self.assertRaisesRegex(OperationError, "consistency"):
            validate_database_state(state, evidence, "12")

        self.assertIn(
            {"name": "tenant.active_batch", "status": "failed"},
            evidence.checks,
        )

    def test_smoke_records_only_safe_status_and_created_resource_id(self):
        args = argparse.Namespace(
            base_url="http://127.0.0.1:8081",
            allow_http=True,
            allow_writes=True,
            tenant_key=None,
            timeout=5.0,
        )
        evidence = Evidence("smoke")
        transport = FakeTransport()

        run_smoke(
            args,
            evidence,
            transport,
            {"SMOKE_USERNAME": "operator", "SMOKE_PASSWORD": "secret-password"},
        )

        serialized = json.dumps(evidence.as_dict())
        self.assertNotIn("secret-password", serialized)
        self.assertNotIn("secret-access-token", serialized)
        self.assertEqual("report-1", evidence.artifacts["createdReportDraftId"])
        authenticated = [
            kwargs for _, kwargs in transport.calls
            if isinstance(kwargs.get("headers"), dict)
        ]
        self.assertTrue(any(
            item["headers"].get("Authorization") == "Bearer secret-access-token"
            for item in authenticated
        ))
        requested_paths = [path for path, _ in transport.calls]
        self.assertIn("/api/reports/drafts", requested_paths)
        self.assertIn("/api/report-jobs", requested_paths)
        self.assertIn("/api/report-deliveries", requested_paths)

    def test_plain_http_is_limited_to_explicit_loopback(self):
        self.assertEqual(
            "http://127.0.0.1:8081",
            validate_base_url("http://127.0.0.1:8081/", True),
        )
        with self.assertRaisesRegex(OperationError, "must use HTTPS"):
            validate_base_url("http://staging.example.com", True)

    def test_bootstrap_password_change_keeps_credentials_out_of_evidence(self):
        args = argparse.Namespace(
            base_url="http://127.0.0.1:8081",
            allow_http=True,
            timeout=5.0,
        )
        evidence = Evidence("bootstrap-password")
        transport = FakeTransport(must_change_password=True)

        run_bootstrap_password(
            args,
            evidence,
            transport,
            {
                "BOOTSTRAP_USERNAME": "initial-admin",
                "BOOTSTRAP_CURRENT_PASSWORD": "temporary-password",
                "BOOTSTRAP_NEW_PASSWORD": "replacement-password",
            },
        )

        serialized = json.dumps(evidence.as_dict())
        self.assertNotIn("temporary-password", serialized)
        self.assertNotIn("replacement-password", serialized)
        self.assertEqual("passed", evidence.checks[-1]["status"])

    def test_evidence_json_write_never_overwrites_existing_file(self):
        from ops.deployment.operations import write_json

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory, "evidence.json")
            output.write_text("existing", encoding="utf-8")
            with self.assertRaisesRegex(OperationError, "already exists"):
                write_json(output, Evidence("smoke").as_dict())
            self.assertEqual("existing", output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
