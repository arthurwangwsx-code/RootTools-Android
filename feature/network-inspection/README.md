# Network Inspection core

Canonical Android-free packet and plaintext parsing contracts for RootTools network inspection.

Current contents:

- PCAP parsing for DNS, TCP, UDP, TLS/SNI, QUIC, HTTP and ICMP;
- protocol, flow and packet summary models;
- decrypted HTTP/WebSocket/TCP event models;
- credential-aware plaintext preview and HTTP metadata parsing;
- host JVM characterization tests imported with the original Net-Tools history.

Root capture, `pcapd`, iptables, MITM add-on, CA overlay and lifecycle code are intentionally not in this module yet. Those paths still use the legacy Net-Tools `su -c` executor and must move through RootTools typed Controllers and audit/rollback contracts before the main App can call them.

```bash
./gradlew :feature:network-inspection:test
```
