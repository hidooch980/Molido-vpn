# Engine feasibility and release gates

| Engine | Current implementation status | License / integration notes | Release gate |
|---|---|---|---|
| WireGuard | Official Android tunnel library integrated on hardening branch; device validation pending | com.wireguard.android:tunnel:1.0.20260102, Apache-2.0; native wireguard-go backend | Resolve/build, inspect merged manifest and ABIs, verify real traffic, DNS, IPv4/IPv6, reconnect and lockdown |
| Xray-core | Not integrated | Select exact upstream release and protocol set; audit all transitive licenses | Pinned source, notices, TUN integration, device and security tests |
| AmneziaWG | Not integrated | Must use a compatible maintained client and server profile; not interchangeable with vanilla WireGuard | Confirm protocol compatibility, source license, packet-path tests |
| MASQUE / CONNECT-IP | Research | Requires a maintained HTTP/3 CONNECT-IP client stack | Interoperability, QUIC, TUN routing, battery and license review |
| Cloudflare WARP | Research | Only supported official integration and terms; no reverse engineering or unsupported account APIs | Written compatibility and legal review |
| Psiphon | Research | SDK/API and redistribution terms need verification | Maintained SDK, privacy review, device tests |
| Tor | Research | Requires maintained Android integration and careful DNS/stream isolation | Bridge, DNS, IPv6, app-routing and device tests |
| SHARD | Research only | No specific interoperable protocol implementation identified | Require protocol specification, maintained auditable source, license and test vectors |

## Status vocabulary
- Planned: no implementation claim.
- Integrated: source dependency and adapter are wired; compilation and tests still required.
- Device-verified: real-device tunnel and traffic tests pass.
- Production: release security, license, performance and regression gates pass.

Never label an engine available based only on a UI entry, process start, TUN creation, or handshake.
