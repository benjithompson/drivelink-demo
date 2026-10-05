#!/usr/bin/env python3
"""Local reference mock of the DriveLink virtual service.

Serves the examples in api/examples/ with the resolution rules in scripts/mock_resolver.py
(the same rules the BlazeMeter transactions encode). Request bodies are read and
ignored. Logs one line per request:
  METHOD path scenario -> status example

Usage:
  scripts/mock-server.py                         listen on 127.0.0.1:8080, with think time
  scripts/mock-server.py --port 9090 --no-delay  other port, respond at once
  curl -H 'X-Scenario: low-battery' http://127.0.0.1:8080/v1/vehicles/DLEV26AURA0000101/status
"""
import argparse
import json
import sys
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import mock_resolver

PROBLEM_JSON = "application/problem+json"


def make_handler(contract, delay):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def handle_one(self):
            length = int(self.headers.get("Content-Length") or 0)
            if length:
                self.rfile.read(length)
            headers = dict(self.headers.items())
            correlation_id = self.headers.get("X-Correlation-Id") or str(uuid.uuid4())
            entry, info = mock_resolver.resolve(contract, self.command, self.path, headers)
            if delay:
                time.sleep(mock_resolver.think_time_ms(contract, info["scenario"], info["operationId"]) / 1000)
            if entry is None:
                status, problem = mock_resolver.error_for(info, self.command, self.path, correlation_id)
                body = json.dumps(problem, indent=2).encode() + b"\n"
                content_type, extra, name = PROBLEM_JSON, {}, info["status"]
            else:
                status, content_type, extra, name = (entry["status"], entry["contentType"],
                                                     entry["headers"], entry["example"])
                body = mock_resolver.body_bytes(entry)
            self.send(status, content_type, body, extra, correlation_id)
            print(f"{self.command} {self.path} {info['scenario']} -> {status} {name}", flush=True)

        def send(self, status, content_type, body, extra, correlation_id):
            self.send_response(status)
            if body:
                self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            for name, value in extra.items():
                self.send_header(name, value)
            self.send_header("X-Correlation-Id", correlation_id)
            self.end_headers()
            if body:
                self.wfile.write(body)

        do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = handle_one

        def log_message(self, *args):
            pass

    return Handler


def main():
    parser = argparse.ArgumentParser(description="DriveLink reference mock server")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--no-delay", action="store_true", help="disable scenario think time")
    args = parser.parse_args()
    contract = mock_resolver.load_contract()
    server = ThreadingHTTPServer((args.host, args.port), make_handler(contract, not args.no_delay))
    print(f"DriveLink mock on http://{args.host}:{args.port}{contract['basePath']} "
          f"({len(contract['index'])} examples, delay {'off' if args.no_delay else 'on'})", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    sys.exit(0)


if __name__ == "__main__":
    main()
