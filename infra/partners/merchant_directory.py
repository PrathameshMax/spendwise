"""Production Debugging Lab: stand-in for an external partner API.

MerchantDirectory is a third-party service that returns a display name, a
category and a logo for a merchant code (for example "BIGBASKET"). In the lab
it runs as a container on the compose network, but it is not part of the
SpendWise platform: its logs are not shipped to Kibana or Splunk and it has
no metrics, exactly like a real vendor's API.

    POST /v1/merchants/lookup   {"names": ["BIGBASKET", "SWIGGY"]}
      -> {"merchants": [{"name", "displayName", "category", "logoUrl"}]}

Vendor-side controls (used only when acting as the vendor):

    GET  /__admin/status
    POST /__admin/mode/healthy | /__admin/mode/degraded | /__admin/mode/auto

"auto" (the default) follows the vendor's own behaviour over time.
"""

import json
import os
import random
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("PORT", "8099"))
BASE_LATENCY_MS = (40, 120)
AUTO_THRESHOLD = int(os.environ.get("AUTO_THRESHOLD", "2400"))
SLOW_FRACTION = float(os.environ.get("SLOW_FRACTION", "0.95"))
SLOW_SECONDS = (
    float(os.environ.get("SLOW_MIN_SECONDS", "120")),
    float(os.environ.get("SLOW_MAX_SECONDS", "240")),
)

CATEGORIES = {
    "BIGBASKET": "Groceries", "BLINKIT": "Groceries", "ZEPTO": "Groceries", "DMART": "Groceries",
    "SWIGGY": "Food & dining", "ZOMATO": "Food & dining", "DOMINOS": "Food & dining",
    "AMAZON": "Shopping", "FLIPKART": "Shopping", "MYNTRA": "Shopping", "AJIO": "Shopping",
    "IRCTC": "Travel", "UBER": "Travel", "OLA": "Travel", "INDIGO": "Travel", "MAKEMYTRIP": "Travel",
    "JIO": "Mobile & internet", "AIRTEL": "Mobile & internet", "VI": "Mobile & internet",
    "NETFLIX": "Entertainment", "HOTSTAR": "Entertainment", "BOOKMYSHOW": "Entertainment",
    "APOLLO PHARMACY": "Health", "PHARMEASY": "Health",
}

_state = {"mode": "auto", "lookups": 0}
_lock = threading.Lock()


def _degraded_now():
    with _lock:
        _state["lookups"] += 1
        mode, lookups = _state["mode"], _state["lookups"]
    if mode == "healthy":
        return False
    if mode == "degraded":
        return True
    return lookups > AUTO_THRESHOLD


def _merchant(name):
    slug = "".join(c.lower() if c.isalnum() else "-" for c in name).strip("-")
    category = CATEGORIES.get(name.upper(), "Bills & utilities" if "BILL" in name.upper() else "Other")
    return {
        "name": name,
        "displayName": name.title(),
        "category": category,
        "logoUrl": f"https://logos.merchantdirectory.example/{slug}.png",
    }


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "MerchantDirectory/2.4"

    def log_message(self, fmt, *args):
        return

    def _read_body(self):
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            data = b""
            while True:
                size_line = self.rfile.readline().strip()
                size = int(size_line.split(b";")[0] or b"0", 16)
                if size == 0:
                    while self.rfile.readline() not in (b"\r\n", b"\n", b""):
                        pass
                    return data
                data += self.rfile.read(size)
                self.rfile.readline()
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length else b""

    def _send(self, status, payload):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/__admin/status":
            with _lock:
                snapshot = dict(_state)
            snapshot["autoThreshold"] = AUTO_THRESHOLD
            self._send(200, snapshot)
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):
        body = self._read_body()
        if self.path.startswith("/__admin/mode/"):
            mode = self.path.rsplit("/", 1)[-1]
            if mode not in ("healthy", "degraded", "auto"):
                self._send(400, {"error": "mode must be healthy, degraded or auto"})
                return
            with _lock:
                _state["mode"] = mode
            print(f"vendor: mode set to {mode}", flush=True)
            self._send(200, {"mode": mode})
            return
        if self.path != "/v1/merchants/lookup":
            self._send(404, {"error": "not found"})
            return
        try:
            names = json.loads(body or b"{}").get("names") or []
        except ValueError:
            self._send(400, {"error": "invalid JSON"})
            return
        if _degraded_now() and random.random() < SLOW_FRACTION:
            time.sleep(random.uniform(*SLOW_SECONDS))
        else:
            time.sleep(random.uniform(*BASE_LATENCY_MS) / 1000)
        self._send(200, {"merchants": [_merchant(str(n)) for n in names]})


class Server(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 512


if __name__ == "__main__":
    print(f"MerchantDirectory stand-in listening on :{PORT}", flush=True)
    Server(("0.0.0.0", PORT), Handler).serve_forever()
