#!/usr/bin/env python3
"""Deterministic OpenAI-compatible embedding endpoint for release-gate CI."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Sequence


EMBEDDING_DIMENSIONS = 1536
MAX_REQUEST_BYTES = 1024 * 1024


def deterministic_embedding(text: str) -> list[float]:
    encoded = text.encode("utf-8")
    values: list[float] = []
    counter = 0
    while len(values) < EMBEDDING_DIMENSIONS:
        digest = hashlib.sha256(encoded + counter.to_bytes(4, "big")).digest()
        values.extend((byte - 127.5) / 127.5 for byte in digest)
        counter += 1
    values = values[:EMBEDDING_DIMENSIONS]
    norm = math.sqrt(sum(value * value for value in values))
    return [round(value / norm, 8) for value in values]


def build_embedding_response(payload: dict[str, Any]) -> dict[str, Any]:
    raw_input = payload.get("input")
    inputs = [raw_input] if isinstance(raw_input, str) else raw_input
    if not isinstance(inputs, list) or not inputs or not all(isinstance(item, str) for item in inputs):
        raise ValueError("input must be a string or a non-empty list of strings")
    model = payload.get("model")
    if not isinstance(model, str) or not model.strip():
        raise ValueError("model must be a non-empty string")
    data = [
        {
            "object": "embedding",
            "index": index,
            "embedding": deterministic_embedding(item),
        }
        for index, item in enumerate(inputs)
    ]
    token_estimate = sum(max(1, len(item.split())) for item in inputs)
    return {
        "object": "list",
        "data": data,
        "model": model,
        "usage": {"prompt_tokens": token_estimate, "total_tokens": token_estimate},
    }


class EmbeddingHandler(BaseHTTPRequestHandler):
    server_version = "EmbeddingStub/1"

    def do_GET(self) -> None:
        if self.path == "/healthz":
            self._write_json(200, {"status": "UP"})
            return
        self._write_json(404, {"error": {"message": "not found"}})

    def do_POST(self) -> None:
        if self.path != "/v1/embeddings":
            self._write_json(404, {"error": {"message": "not found"}})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length < 1 or length > MAX_REQUEST_BYTES:
                raise ValueError("request body size is invalid")
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            if not isinstance(payload, dict):
                raise ValueError("request body must be an object")
            self._write_json(200, build_embedding_response(payload))
        except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as exception:
            self._write_json(400, {"error": {"message": str(exception)}})

    def log_message(self, format_string: str, *args: Any) -> None:
        return

    def _write_json(self, status: int, payload: dict[str, Any]) -> None:
        content = json.dumps(payload, ensure_ascii=True, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8080)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    server = ThreadingHTTPServer((args.host, args.port), EmbeddingHandler)
    server.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
