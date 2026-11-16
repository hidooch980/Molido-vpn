# Six-engine feasibility and release gates

MolidoVPN AI has approved exactly six engine families for the Iran-focused product. Approval means they are in the target architecture, not that they are already operational.

| Engine | Intended role | Current main-branch truth | Required implementation and release gate |
|---|---|---|---|
| AmneziaWG | WireGuard-derived obfuscated UDP tunnel | Parser and placeholder adapter only; no real AmneziaWG packet transport | Integrate a maintained compatible Android runtime; verify obfuscation parameters, server interoperability, socket protection, TUN forwarding, license and ABI |
| Xray-core | Flexible proxy transport; VLESS + REALITY first | URI parser and placeholder adapter only; no Xray native runtime or packet bridge | Pin upstream release; integrate native runtime and app-owned TUN bridge; verify REALITY/TLS identity, UDP behavior, lifecycle, license and notices |
| sing-box | Multi-protocol runtime | Not integrated | Integrate official Android libbox; select supported protocols explicitly; avoid second TUN owner; review license/source obligations and ABI |
| WireGuard | Fast modern tunnel | Main-branch adapter is a placeholder. A separate hardening PR contains official Android backend integration but is unmerged and device-unverified | Merge only after CI and conflict review; verify upstream license, ABI packaging, key handling, IPv4/IPv6, DNS, socket protection, reconnect and physical-device traffic |
| Hysteria 2 | QUIC/UDP alternative for lossy networks | Not integrated | Pin maintained client runtime; test UDP reachability, loss, congestion, MTU, packet forwarding, socket protection, battery and license |
| Shadowsocks 2022 | AEAD encrypted proxy and UDP relay | Not integrated | Use maintained 2022 methods; verify replay protection, key handling, UDP relay, TUN bridge, license and notices |

## Status vocabulary

- Planned: no runtime implementation.
- Prototype only: parser/adapter or UI exists, but no real packet transport.
- Integrated, unverified: pinned upstream runtime is wired and automated integration tests pass.
- Device-verified: real-device bidirectional traffic, DNS, IPv6, kill switch and recovery tests pass.
- Production: release security, license, performance and regression gates pass.

## Selection and release policy

- Do not label an engine available based only on a UI entry, config parser, binary inclusion, process start, or synthetic handshake.
- Every engine uses the single app-owned Android VpnService/TUN. Adapters are transport backends.
- Protect outbound sockets with the real VpnService socket-protection API to prevent VPN recursion.
- Report Connected only after bidirectional traffic and external egress verification.
- Prioritize measured stability and packet loss over peak throughput; use cooldown/hysteresis to avoid route flapping.
- Test across multiple authorized Iranian mobile and fixed networks. No engine is guaranteed to work on every provider or at every time.
- Record immutable upstream version/commit, license expression, transitive dependencies, native ABIs, notices, checksum/signature provenance and update policy before redistribution.
- No engine is selectable in production until it reaches Production readiness. No APK release before physical-device acceptance and owner signing-key verification.
