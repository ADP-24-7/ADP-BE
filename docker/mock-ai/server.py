import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/health":
            self.send_error(404)
            return
        self._json_response(200, {"status": "ok"})

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_error(404)
            return
        try:
            json.loads(self._read_body())
        except (ValueError, json.JSONDecodeError):
            self._json_response(400, {"error": "invalid_request"})
            return
        self._json_response(200, {"answer": "Approved context processed"})

    def _read_body(self):
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks = []
            while True:
                chunk_size = int(self.rfile.readline().strip(), 16)
                if chunk_size == 0:
                    self.rfile.readline()
                    break
                chunks.append(self.rfile.read(chunk_size))
                self.rfile.read(2)
            return b"".join(chunks)
        content_length = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(content_length)

    def log_message(self, format, *args):
        return

    def _json_response(self, status, body):
        payload = json.dumps(body, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("X-ADP-Response-Schema-Version", "ai-provider-response/v1")
        self.end_headers()
        self.wfile.write(payload)


ThreadingHTTPServer(("0.0.0.0", 8090), Handler).serve_forever()
