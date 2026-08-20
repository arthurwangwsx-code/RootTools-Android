package com.arthur.roottools.integration.termux

/**
 * Builds a dependency-free Python MCP relay for Termux.
 *
 * The relay intentionally exposes a fixed tool catalog backed by RootTools semantic automation
 * actions. It has no shell/exec tool, never binds a wildcard address, and supports the current
 * MCP 2026-07-28 stateless core over stdio or request-scoped HTTP POST.
 */
object TermuxMcpRelayScriptBuilder {
    const val VERSION = 2
    const val MCP_PROTOCOL_VERSION = "2026-07-28"
    const val HTTP_PORT = 8765

    fun build(
        deviceId: String,
        rootToolsAutomationToken: String,
        relayBearerToken: String,
    ): String {
        require(DEVICE_ID_REGEX.matches(deviceId)) { "Invalid developer device id" }
        require(TOKEN_REGEX.matches(rootToolsAutomationToken)) { "Invalid RootTools automation token" }
        require(TOKEN_REGEX.matches(relayBearerToken)) { "Invalid relay bearer token" }

        return """
            #!/data/data/com.termux/files/usr/bin/python
            import argparse
            import hmac
            import http.server
            import ipaddress
            import json
            import re
            import secrets
            import subprocess
            import sys
            import threading
            import time
            from collections import deque

            PROTOCOL_VERSION = "$MCP_PROTOCOL_VERSION"
            SERVER_VERSION = "roottools-termux-mcp/$VERSION"
            DEVICE_ID = "$deviceId"
            ROOTTOOLS_TOKEN = "$rootToolsAutomationToken"
            RELAY_BEARER_TOKEN = "$relayBearerToken"
            ROOTTOOLS_COMPONENT = "com.arthur.roottools/.automation.ActionRouterReceiver"
            ROOTTOOLS_ACTION = "com.arthur.roottools.ACTION"
            HTTP_PORT = $HTTP_PORT
            MAX_BODY_BYTES = 65536
            RATE_LIMIT_PER_MINUTE = 60

            PACKAGE_RE = re.compile(r"^[A-Za-z0-9._]{1,200}${'$'}")
            RATE_LOCK = threading.Lock()
            RATE_WINDOW = deque()

            TOOLS = [
                {
                    "name": "get_device_identity",
                    "title": "Get RootTools device identity",
                    "description": "Read the stable RootTools developer device ID and MCP relay version without invoking Android shell.",
                    "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
                },
                {
                    "name": "get_device_status",
                    "title": "Get RootTools device status",
                    "description": "Read RootTools root, performance, ADB and Tailscale status.",
                    "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
                },
                {
                    "name": "set_performance_mode",
                    "title": "Set RootTools performance mode",
                    "description": "Set one of RootTools AUTO, COOL or PERFORMANCE semantic modes.",
                    "inputSchema": {
                        "type": "object",
                        "properties": {"mode": {"type": "string", "enum": ["auto", "cool", "performance"]}},
                        "required": ["mode"],
                        "additionalProperties": False,
                    },
                },
                {
                    "name": "ensure_root_adb",
                    "title": "Ensure Root TCP ADB is enabled",
                    "description": "Enable the RootTools TCP ADB management path. This tool cannot disable it.",
                    "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
                },
                {
                    "name": "run_diagnostic",
                    "title": "Run RootTools diagnostic snapshot",
                    "description": "Create a RootTools diagnostic snapshot and return its artifact name.",
                    "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
                },
                {
                    "name": "freeze_app",
                    "title": "Freeze an Android app",
                    "description": "Freeze a validated package through the RootTools package policy controller.",
                    "inputSchema": {
                        "type": "object",
                        "properties": {"package": {"type": "string", "pattern": "^[A-Za-z0-9._]+${'$'}"}},
                        "required": ["package"],
                        "additionalProperties": False,
                    },
                },
                {
                    "name": "enable_app",
                    "title": "Enable an Android app",
                    "description": "Re-enable a validated package through the RootTools package policy controller.",
                    "inputSchema": {
                        "type": "object",
                        "properties": {"package": {"type": "string", "pattern": "^[A-Za-z0-9._]+${'$'}"}},
                        "required": ["package"],
                        "additionalProperties": False,
                    },
                },
                {
                    "name": "run_workflow",
                    "title": "Run a RootTools managed workflow",
                    "description": "Run one fixed RootTools cross-feature workflow. No arbitrary workflow steps or shell commands are accepted.",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "workflow": {
                                "type": "string",
                                "enum": ["test_device_ready", "app_test_ready", "diagnostic_pipeline", "developer_runtime_health"],
                            },
                            "package": {"type": "string", "pattern": "^[A-Za-z0-9._]+${'$'}"},
                        },
                        "required": ["workflow"],
                        "additionalProperties": False,
                    },
                },
            ]
            TOOL_BY_NAME = {tool["name"]: tool for tool in TOOLS}

            class RpcFailure(Exception):
                def __init__(self, code, message, data=None):
                    super().__init__(message)
                    self.code = code
                    self.message = message
                    self.data = data

            def json_response(request_id, result):
                return {"jsonrpc": "2.0", "id": request_id, "result": result}

            def json_error(request_id, code, message, data=None):
                error = {"code": code, "message": message}
                if data is not None:
                    error["data"] = data
                return {"jsonrpc": "2.0", "id": request_id, "error": error}

            def server_meta():
                return {
                    "io.modelcontextprotocol/serverInfo": {
                        "name": "roottools-android",
                        "version": SERVER_VERSION,
                    }
                }

            def check_rate_limit():
                now = time.monotonic()
                with RATE_LOCK:
                    while RATE_WINDOW and now - RATE_WINDOW[0] >= 60.0:
                        RATE_WINDOW.popleft()
                    if len(RATE_WINDOW) >= RATE_LIMIT_PER_MINUTE:
                        raise RpcFailure(-32000, "RootTools MCP relay rate limit exceeded")
                    RATE_WINDOW.append(now)

            def validate_request(message):
                if not isinstance(message, dict) or message.get("jsonrpc") != "2.0":
                    raise RpcFailure(-32600, "Invalid JSON-RPC request")
                if "id" not in message:
                    raise RpcFailure(-32600, "RootTools MCP relay accepts requests, not notifications")
                method = message.get("method")
                params = message.get("params", {})
                if not isinstance(method, str) or not isinstance(params, dict):
                    raise RpcFailure(-32600, "Invalid request method or params")
                meta = params.get("_meta")
                if not isinstance(meta, dict):
                    raise RpcFailure(-32602, "MCP 2026-07-28 request _meta is required")
                if meta.get("io.modelcontextprotocol/protocolVersion") != PROTOCOL_VERSION:
                    raise RpcFailure(-32602, "Unsupported MCP protocol version")
                client_info = meta.get("io.modelcontextprotocol/clientInfo")
                if client_info is not None and not isinstance(client_info, dict):
                    raise RpcFailure(-32602, "MCP clientInfo must be an object when provided")
                if not isinstance(meta.get("io.modelcontextprotocol/clientCapabilities"), dict):
                    raise RpcFailure(-32602, "MCP clientCapabilities is required")
                return method, params

            def progress_token(params):
                meta = params.get("_meta", {}) if isinstance(params, dict) else {}
                token = meta.get("progressToken") if isinstance(meta, dict) else None
                if isinstance(token, bool):
                    return None
                return token if isinstance(token, (str, int, float)) else None

            def progress_notification(token, progress, total, message):
                return {
                    "jsonrpc": "2.0",
                    "method": "notifications/progress",
                    "params": {
                        "progressToken": token,
                        "progress": progress,
                        "total": total,
                        "message": message,
                    },
                }

            def parse_roottools_result(output):
                match = re.search(r'data="((?:\\\\.|[^"\\\\])*)"', output)
                if not match:
                    raise RuntimeError("RootTools ordered broadcast returned no structured data")
                wrapped = '"' + match.group(1) + '"'
                encoded_json = json.loads(wrapped)
                result = json.loads(encoded_json)
                if not isinstance(result, dict):
                    raise RuntimeError("RootTools result is not an object")
                return result

            def call_roottools(command, extras=None):
                request_id = "mcp-" + secrets.token_hex(8)
                argv = [
                    "/system/bin/am", "broadcast", "-W",
                    "-n", ROOTTOOLS_COMPONENT,
                    "-a", ROOTTOOLS_ACTION,
                    "--es", "token", ROOTTOOLS_TOKEN,
                    "--es", "request_id", request_id,
                    "--es", "command", command,
                ]
                extras = extras or []
                argv.extend(extras)
                completed = subprocess.run(
                    argv,
                    check=False,
                    capture_output=True,
                    text=True,
                    timeout=45,
                )
                output = (completed.stdout or "") + (completed.stderr or "")
                return parse_roottools_result(output)

            def validate_empty_arguments(arguments):
                if arguments not in ({}, None):
                    raise RpcFailure(-32602, "This tool accepts no arguments")

            def call_tool(name, arguments):
                if name not in TOOL_BY_NAME:
                    raise RpcFailure(-32602, "Unknown RootTools tool")
                if arguments is None:
                    arguments = {}
                if not isinstance(arguments, dict):
                    raise RpcFailure(-32602, "Tool arguments must be an object")

                if name == "get_device_identity":
                    validate_empty_arguments(arguments)
                    result = {
                        "success": True,
                        "message": "RootTools device identity ready",
                        "payload": {
                            "deviceId": DEVICE_ID,
                            "relayVersion": SERVER_VERSION,
                            "protocolVersion": PROTOCOL_VERSION,
                        },
                    }
                elif name == "get_device_status":
                    validate_empty_arguments(arguments)
                    result = call_roottools("GET_STATUS")
                elif name == "set_performance_mode":
                    if set(arguments.keys()) != {"mode"} or arguments.get("mode") not in {"auto", "cool", "performance"}:
                        raise RpcFailure(-32602, "mode must be auto, cool or performance")
                    result = call_roottools("SET_MODE", ["--es", "mode", arguments["mode"]])
                elif name == "ensure_root_adb":
                    validate_empty_arguments(arguments)
                    result = call_roottools("SET_ADB", ["--ez", "enabled", "true"])
                elif name == "run_diagnostic":
                    validate_empty_arguments(arguments)
                    result = call_roottools("RUN_DIAGNOSTIC")
                elif name in {"freeze_app", "enable_app"}:
                    if set(arguments.keys()) != {"package"}:
                        raise RpcFailure(-32602, "package is required")
                    package_name = arguments.get("package")
                    if not isinstance(package_name, str) or not PACKAGE_RE.fullmatch(package_name):
                        raise RpcFailure(-32602, "Invalid Android package name")
                    command = "FREEZE" if name == "freeze_app" else "UNFREEZE"
                    result = call_roottools(command, ["--es", "package", package_name])
                elif name == "run_workflow":
                    allowed_keys = {"workflow", "package"}
                    if not set(arguments.keys()).issubset(allowed_keys) or "workflow" not in arguments:
                        raise RpcFailure(-32602, "workflow is required")
                    workflow = arguments.get("workflow")
                    if workflow not in {"test_device_ready", "app_test_ready", "diagnostic_pipeline", "developer_runtime_health"}:
                        raise RpcFailure(-32602, "Unknown managed workflow")
                    package_name = arguments.get("package")
                    if workflow == "app_test_ready":
                        if not isinstance(package_name, str) or not PACKAGE_RE.fullmatch(package_name):
                            raise RpcFailure(-32602, "app_test_ready requires a valid package")
                    elif package_name is not None:
                        raise RpcFailure(-32602, "This workflow does not accept a package")
                    extras = ["--es", "workflow", workflow]
                    if package_name is not None:
                        extras.extend(["--es", "package", package_name])
                    result = call_roottools("RUN_WORKFLOW", extras)
                else:
                    raise RpcFailure(-32602, "Unknown RootTools tool")

                success = bool(result.get("success"))
                text = result.get("message") if isinstance(result.get("message"), str) else json.dumps(result, separators=(",", ":"))
                return {
                    "resultType": "complete",
                    "content": [{"type": "text", "text": text}],
                    "structuredContent": result,
                    "isError": not success,
                    "_meta": server_meta(),
                }

            def handle_rpc(message, notify=None):
                request_id = message.get("id") if isinstance(message, dict) else None
                try:
                    check_rate_limit()
                    method, params = validate_request(message)
                    if method == "server/discover":
                        result = {
                            "resultType": "complete",
                            "supportedVersions": [PROTOCOL_VERSION],
                            "capabilities": {"tools": {}},
                            "instructions": "RootTools Android privileged tools. No arbitrary shell or terminal execution is exposed.",
                            "ttlMs": 3600000,
                            "cacheScope": "private",
                            "_meta": server_meta(),
                        }
                    elif method == "tools/list":
                        cursor = params.get("cursor")
                        if cursor not in (None, ""):
                            raise RpcFailure(-32602, "Pagination cursor is not supported")
                        result = {
                            "resultType": "complete",
                            "tools": TOOLS,
                            "_meta": server_meta(),
                        }
                    elif method == "tools/call":
                        name = params.get("name")
                        if not isinstance(name, str):
                            raise RpcFailure(-32602, "Tool name is required")
                        token = progress_token(params)
                        if token is not None and notify is not None:
                            notify(progress_notification(token, 0, 1, "RootTools tool started"))
                        result = call_tool(name, params.get("arguments", {}))
                        if token is not None and notify is not None:
                            notify(progress_notification(token, 1, 1, "RootTools tool completed"))
                    else:
                        raise RpcFailure(-32601, "Method not found")
                    return json_response(request_id, result)
                except RpcFailure as error:
                    return json_error(request_id, error.code, error.message, error.data)
                except subprocess.TimeoutExpired:
                    return json_error(request_id, -32000, "RootTools action timed out")
                except Exception as error:
                    return json_error(request_id, -32603, "Internal RootTools relay error", type(error).__name__)

            def validate_http_headers(handler, message):
                method = message.get("method") if isinstance(message, dict) else None
                params = message.get("params", {}) if isinstance(message, dict) else {}
                meta = params.get("_meta", {}) if isinstance(params, dict) else {}
                body_version = meta.get("io.modelcontextprotocol/protocolVersion") if isinstance(meta, dict) else None
                header_version = handler.headers.get("MCP-Protocol-Version")
                header_method = handler.headers.get("Mcp-Method")
                if header_version != body_version or header_version != PROTOCOL_VERSION:
                    raise RpcFailure(-32020, "HeaderMismatch: MCP-Protocol-Version")
                if header_method != method:
                    raise RpcFailure(-32020, "HeaderMismatch: Mcp-Method")
                if method == "tools/call":
                    name = params.get("name") if isinstance(params, dict) else None
                    if handler.headers.get("Mcp-Name") != name:
                        raise RpcFailure(-32020, "HeaderMismatch: Mcp-Name")

            def validate_http_accept(handler):
                accept = handler.headers.get("Accept", "").lower()
                if "application/json" not in accept or "text/event-stream" not in accept:
                    raise RpcFailure(-32600, "Accept must include application/json and text/event-stream")

            def bearer_authorized(value):
                prefix = "Bearer "
                if not isinstance(value, str) or not value.startswith(prefix):
                    return False
                return hmac.compare_digest(value[len(prefix):], RELAY_BEARER_TOKEN)

            class McpHandler(http.server.BaseHTTPRequestHandler):
                server_version = "RootToolsMCP"

                def log_message(self, format_string, *args):
                    sys.stderr.write("roottools-mcp: " + (format_string % args) + "\n")

                def send_json(self, status, payload):
                    encoded = json.dumps(payload, separators=(",", ":")).encode("utf-8")
                    self.send_response(status)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", str(len(encoded)))
                    self.send_header("Cache-Control", "no-store")
                    self.end_headers()
                    self.wfile.write(encoded)

                def begin_sse(self):
                    self.send_response(200)
                    self.send_header("Content-Type", "text/event-stream")
                    self.send_header("Cache-Control", "no-store")
                    self.send_header("X-Accel-Buffering", "no")
                    self.send_header("Connection", "close")
                    self.end_headers()
                    self.close_connection = True

                def send_sse_event(self, payload):
                    encoded = json.dumps(payload, separators=(",", ":")).encode("utf-8")
                    self.wfile.write(b"data: " + encoded + b"\n\n")
                    self.wfile.flush()

                def do_GET(self):
                    self.send_error(405)

                def do_POST(self):
                    if self.path != "/mcp":
                        self.send_error(404)
                        return
                    if not bearer_authorized(self.headers.get("Authorization")):
                        self.send_response(401)
                        self.send_header("WWW-Authenticate", "Bearer")
                        self.send_header("Content-Length", "0")
                        self.end_headers()
                        return
                    content_type = self.headers.get("Content-Type", "")
                    if not content_type.lower().startswith("application/json"):
                        self.send_error(415)
                        return
                    try:
                        validate_http_accept(self)
                    except RpcFailure as error:
                        self.send_json(400, json_error(None, error.code, error.message))
                        return
                    try:
                        length = int(self.headers.get("Content-Length", "0"))
                    except ValueError:
                        self.send_error(400)
                        return
                    if length <= 0 or length > MAX_BODY_BYTES:
                        self.send_error(413 if length > MAX_BODY_BYTES else 400)
                        return
                    try:
                        message = json.loads(self.rfile.read(length).decode("utf-8"))
                    except Exception:
                        self.send_json(400, json_error(None, -32700, "Parse error"))
                        return
                    try:
                        validate_http_headers(self, message)
                    except RpcFailure as error:
                        request_id = message.get("id") if isinstance(message, dict) else None
                        self.send_json(400, json_error(request_id, error.code, error.message))
                        return
                    params = message.get("params", {}) if isinstance(message, dict) else {}
                    if message.get("method") == "tools/call" and progress_token(params) is not None:
                        self.begin_sse()
                        response = handle_rpc(message, notify=self.send_sse_event)
                        self.send_sse_event(response)
                    else:
                        self.send_json(200, handle_rpc(message))

            def resolve_bind(mode):
                if mode == "loopback":
                    return "127.0.0.1"
                if mode == "tailscale":
                    status = call_roottools("GET_STATUS")
                    payload = status.get("payload") if isinstance(status, dict) else None
                    candidate = payload.get("tailscaleIpv4") if isinstance(payload, dict) else None
                    if not isinstance(candidate, str):
                        raise RuntimeError("RootTools did not report a Tailscale IPv4 address")
                    address = ipaddress.ip_address(candidate)
                    if address not in ipaddress.ip_network("100.64.0.0/10"):
                        raise RuntimeError("Reported address is outside the Tailscale CGNAT IPv4 range")
                    return candidate
                raise RuntimeError("Only loopback or tailscale binding is allowed")

            def run_http(bind_mode):
                bind_address = resolve_bind(bind_mode)
                server = http.server.ThreadingHTTPServer((bind_address, HTTP_PORT), McpHandler)
                server.daemon_threads = True
                sys.stderr.write("roottools-mcp listening on %s:%d\n" % (bind_address, HTTP_PORT))
                server.serve_forever()

            def run_stdio():
                def notify(payload):
                    sys.stdout.write(json.dumps(payload, separators=(",", ":")) + "\n")
                    sys.stdout.flush()

                for raw in sys.stdin:
                    raw = raw.strip()
                    if not raw:
                        continue
                    try:
                        message = json.loads(raw)
                        response = handle_rpc(message, notify=notify)
                    except Exception:
                        response = json_error(None, -32700, "Parse error")
                    sys.stdout.write(json.dumps(response, separators=(",", ":")) + "\n")
                    sys.stdout.flush()

            def main():
                parser = argparse.ArgumentParser(description="RootTools Termux MCP relay")
                parser.add_argument("--transport", choices=["stdio", "http"], required=True)
                parser.add_argument("--bind", choices=["loopback", "tailscale"], default="loopback")
                args = parser.parse_args()
                if args.transport == "stdio":
                    run_stdio()
                else:
                    run_http(args.bind)

            if __name__ == "__main__":
                main()
        """.trimIndent() + "\n"
    }

    private val TOKEN_REGEX = Regex("^[A-Za-z0-9_-]{48,128}$")
    private val DEVICE_ID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
}

