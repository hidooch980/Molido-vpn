# Roadmap

## Phase 0 — repository baseline
- [x] Establish independent repository and product/security principles.
- [x] Document architecture and engine feasibility gates.
- [ ] Add Android Gradle project skeleton and pinned toolchain.
- [ ] Add CI for formatting, static analysis, unit tests, and debug build.

## Phase 1 — reliable VPN foundation
- [ ] Compose app shell and navigation.
- [ ] Permission flow and foreground Android VpnService.
- [ ] TUN lifecycle, route policy, DNS configuration, IPv4/IPv6 handling.
- [ ] Engine adapter contract, state machine, structured diagnostics.
- [ ] Connection verification and fail-safe disconnect behavior.

## Phase 2 — first production engines
- [ ] WireGuard adapter and official-compatible configuration handling.
- [ ] Xray adapter after dependency/license/security review.
- [ ] AmneziaWG adapter after compatibility and licensing review.
- [ ] Device tests: connect, reconnect, process death, network handover, DNS, IPv6, lockdown.

## Phase 3 — additional engines
- [ ] Evaluate MASQUE/CONNECT-IP implementation and HTTP/3 stack.
- [ ] Evaluate official WARP integration constraints.
- [ ] Evaluate Psiphon SDK/API and redistribution terms.
- [ ] Evaluate Tor integration, bridges, and DNS behavior.
- [ ] Keep SHARD research-only until a verifiable implementation exists.

## Phase 4 — adaptive intelligence and product hardening
- [ ] Deterministic baseline route scoring before any ML.
- [ ] On-device adaptive scoring from privacy-minimized network measurements.
- [ ] User controls, country/IP metadata, speed and stability history.
- [ ] Split tunneling, per-app routing, safe update mechanism.
- [ ] SBOM, dependency audit, release signing, reproducible build review.

## Release gate
No engine or security feature is marked production-ready until code review, license review, automated tests, and real Android device tests pass. A successful handshake alone is not proof of working traffic.
