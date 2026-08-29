# Packet Inspection UX & Interaction Specification

## Product goal

RootTools Network Inspection must support a full drill-down path:

`Capture session → Summary / Packets / Flows → Individual packet → protocol-aware fields → text/hex payload`

Raw PCAP remains the source of truth. The UI builds a bounded local packet index for fast browsing and never mutates the original capture.

## Interaction architecture

Within the RootTools **Network** destination, the structure is **Diagnostics / Capture / TLS inspection**. Detail destinations use the standard Back action and maximize inspection space.

The Traffic workflow is: choose target → capture → stop/analyze → open session → switch between Summary, Packets and Flows → tap a packet for protocol-aware detail. Historical sessions created before packet indexing are lazily rebuilt from their PCAP when first opened, so users do not need to recapture traffic.

## Packet list

Each row shows packet number, detected protocol, a semantic title, source → destination, byte size and a compact protocol hint. Metadata stores at most the first 2,000 packet summaries for predictable memory/storage use; the complete PCAP remains available as evidence.

## Protocol-specific detail

Every detail page begins with common capture/network information, then renders protocol-specific fields.

- **DNS**: transaction ID, query/response, name, UDP endpoints, text/hex payload. Next step: qtype/class, rcode, answers and TTL.
- **TCP**: ports, flags, sequence, acknowledgement, window, printable payload and hex.
- **UDP**: ports, UDP length and payload.
- **HTTP/1.x**: request/status line, parsed headers, underlying TCP metadata, text and hex payload.
- **TLS**: TLS record content type, record version, ClientHello SNI when present, TCP metadata and encrypted bytes. Plaintext remains in the Decrypt workspace.
- **QUIC**: UDP/443 endpoints, Long/Short Header and version when exposed by a Long Header, plus encrypted bytes.
- **ICMP / unsupported IP**: ICMP type/code when available and generic raw-byte fallback for everything else.

Protocol identity uses Material theme emphasis instead of hard-coded per-protocol colors. Semantic fields appear before raw bytes; monospace is reserved for long technical values and payloads. Empty/loading states are explicit rather than blank.

## Data model

`CaptureAnalysis` contains aggregate `ProtocolCount`, aggregate `FlowSummary`, and bounded `PacketSummary` records. A packet stores common metadata, protocol-aware `PacketField` entries and at most a 256-byte text/hex preview. Session metadata lives beside the PCAP.

## Extension roadmap

The decoder contract should remain stable while adding DNS answers/types/TTL, IPv6 extension headers and fragmentation, HTTP/2 frames/stream grouping, WebSocket frames, gRPC framing, protobuf views, richer QUIC Initial metadata, and TCP stream reassembly.

Packet inspection and reconstructed/decrypted message inspection remain separate: packets represent capture-granularity evidence; messages represent semantic application traffic.

## Acceptance criteria

- Saved sessions expose Summary, Packets and Flows without recapturing.
- A packet opens a stable detail route and Back returns to the session.
- DNS, TCP, UDP, HTTP, TLS, QUIC and ICMP render differentiated fields/titles.
- Unsupported traffic still exposes raw bytes instead of disappearing.
- HTTP and DNS packet indexing have parser regression tests.
- `testDebugUnitTest`, `lintDebug` and `assembleDebug` pass.
