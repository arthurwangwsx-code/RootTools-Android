# Roadmap

## P0 — power control plane — implemented

- Home list and device/root summary.
- Power dashboard.
- Screen-off work mode.
- Screen sleep/wake sub-page.
- Double-tap-to-wake read/write.
- Runtime load/power telemetry.
- LAN address page.

## P1 — durable background runtime — partially implemented

- [x] Boot restoration (opt-in).
- [x] Foreground runtime owns wake locks and WireGuard engine lifecycle.
- [x] Notification entry and stop-all action.
- [x] CPU usage, load average, memory, network, thermal and energy telemetry.
- Persistent privileged companion process.
- Structured logs, crash/restart history and health state.
- Per-engine CPU/network accounting.
- Battery/power history persisted at a low write frequency.

## P2 — LAN proxy server — first vertical slice implemented

- [x] WireGuard official Android userspace backend.
- [x] Root capability probe chooses userspace backend on the current Redmi.
- [x] IPv4 forwarding and reversible iptables/NAT rules.
- [x] Generated server/test-client key pair and copyable LAN test profile.
- [x] RX/TX traffic totals.
- [ ] Engine abstraction generalized beyond WireGuard.
- Peer/key management and QR/config export.
- LAN listener/port diagnostics.
- Connected peers and handshake state.

## P3 — remote connectivity

- Public IP / NAT reachability diagnostics.
- Port-forwarding checklist and reachability test.
- Optional relay/overlay engine for CGNAT environments.
- DNS and endpoint update support for changing home IPs.

## P4 — additional workloads

- HTTP/SOCKS proxy engines.
- Packet capture integration.
- Remote automation/MCP node.
- Download/file service.
- Unified resource budgets and priority scheduling across engines.
