# MolidoVPN — six-engine integration matrix

Only these six VPN engines are in the product architecture. WARP is not a seventh engine; any WARP support must be treated as a provider/profile integration within the approved architecture, not as an extra selectable transport.

| Order | Engine | Runtime target | Current status | Activation gate |
|---:|---|---|---|---|
| 1 | WireGuard | Official Android GoBackend / wireguard-go | Source-integrated; debug-only; no physical-device acceptance yet | Single TUN ownership, peer handshake, packet forwarding, protected sockets, IPv4/IPv6/DNS tests, handover and recovery on a real phone |
| 2 | Xray-core | Android native runtime; first VLESS + REALITY | Parser only; runtime fail-closed | Pinned native version, license/SBOM, protected sockets, TUN packet forwarding and device tests |
| 3 | AmneziaWG | Compatible AmneziaWG native runtime | Config parser only; runtime fail-closed | Protocol-compatible native implementation, obfuscation parameters, packet forwarding and device tests |
| 4 | sing-box | Official Android libbox integration | Not integrated | Pinned libbox, lifecycle/API audit, single TUN ownership, DNS/routing and device tests |
| 5 | Hysteria 2 | Maintained Hysteria 2 native runtime | URI parser only; runtime fail-closed | QUIC/UDP transport, congestion configuration, protected sockets, TUN forwarding and device tests |
| 6 | Shadowsocks 2022 | Maintained Shadowsocks 2022 runtime | Not integrated | Correct 2022 cipher support, key handling, UDP relay, TUN forwarding and device tests |

## Readiness vocabulary

- **Planned:** no runtime implementation.
- **Parser-only:** profile syntax can be validated; no live tunnel exists.
- **Source-integrated:** native runtime and adapter compile, but device behavior is not certified.
- **Device-verified:** real-device handshake, packet flow, DNS, IPv4/IPv6, teardown and recovery tests pass.
- **Production:** device verification plus security, license, performance, update and regression gates pass.

A catalog entry, parser success, native library presence, or process start is never proof of an operational VPN tunnel.

## Global safety gates

1. Complete WireGuard first. Do not start enabling the other five engines until its single-TUN lifecycle and real packet flow are accepted.
2. Each engine must have exactly one owner for the Android VPN TUN. Never run two Android VPN service owners concurrently.
3. Every engine must protect its own transport sockets from routing back into its TUN.
4. Fail closed on missing native libraries, unsupported ABI, invalid configuration, startup failure, tunnel loss or teardown failure.
5. Never mark an engine selectable/active/production before its own end-to-end device tests pass.
6. Subscription refresh and engine/core updates are separate trust and verification pipelines.
7. DNS-over-HTTPS currently covers app-owned A/AAAA lookups on the active VPN Network only. System-wide UDP/53 interception remains a separate TUN packet-proxy requirement.
