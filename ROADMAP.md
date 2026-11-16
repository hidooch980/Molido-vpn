# Roadmap

## Phase 0 — repository baseline
- [x] Establish independent repository and product/security principles.
- [x] Document architecture and engine feasibility gates.
- [x] Add Android Gradle project skeleton and Compose app shell.
- [x] Add CI workflow for a debug build.
- [x] Run CI and resolve toolchain/build issues (added Gradle 8.13 wrapper and fixed CI execution).

## Phase 1 — reliable VPN foundation
- [x] Compose navigation, connection state and permission flow.
- [x] Foreground Android VpnService with notification and lifecycle handling.
- [x] TUN interface, routes, DNS policy, IPv4/IPv6 handling.
- [x] Engine adapter contract, state machine, structured diagnostics.
- [x] Connection verification and fail-safe disconnect behavior.

## Phase 2 — first production engines
- [x] WireGuard adapter and official-compatible configuration handling.
- [x] Xray adapter (VLESS Reality, Trojan, Shadowsocks parsers & engine adapter).
- [x] AmneziaWG adapter (Obfuscation parameters Jc, Jmin, Jmax, S1, S2, H1..H4).
- [x] Free Cloudflare WARP auto-provisioning and native tunnel adapter.
- [x] Device tests: connect, reconnect, process death, network handover, DNS, IPv6, lockdown.

## Phase 3 — additional engines
- [ ] Evaluate MASQUE/CONNECT-IP implementation and HTTP/3 stack.
- [x] Cloudflare WARP native integration.
- [ ] Evaluate Psiphon SDK/API and redistribution terms.
- [ ] Evaluate Tor integration, bridges, and DNS behavior.
- [ ] Keep SHARD research-only until a verifiable implementation exists.

## Phase 4 — adaptive intelligence and product hardening
- [x] Deterministic baseline route scoring before any ML.
- [ ] On-device adaptive scoring from privacy-minimized network measurements.
- [x] User controls, country/IP metadata, speed and stability history.
- [x] Split tunneling, per-app routing, and subscription import.
- [x] Kill Switch lockdown and DoH anti-censorship DNS integration.
- [x] Automated release build workflow for signed APK artifacts.

## Release gate
No engine or security feature is marked production-ready until code review, license review, automated tests, and real Android device tests pass. A successful handshake alone is not proof of working traffic.
