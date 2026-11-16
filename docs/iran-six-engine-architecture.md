# MolidoVPN AI — Iran-focused six-engine architecture

## Product decision

The approved engine set is exactly:
1. AmneziaWG
2. Xray-core (VLESS / REALITY profile first; only protocols supported by the pinned upstream build)
3. sing-box (a separately integrated multi-protocol runtime)
4. WireGuard
5. Hysteria 2
6. Shadowsocks 2022

Priority: connection stability on Iranian mobile and fixed networks. No protocol is assumed to work on every ISP, region, time, or filtering condition. Selection is based on measured, real network observations, not claims or static rankings.

## Current repository truth

The current `main` branch contains Kotlin adapter-shaped classes for AmneziaWG, Xray and WireGuard, plus a WARP class. These classes construct in-memory session objects and emit `TransportReady`; they do not themselves run the corresponding native tunnel engines or forward packets. The current app Gradle file does not declare the upstream runtime dependencies. These are prototypes, not operational VPN engines.

The hardening PR contains an official WireGuard Android tunnel integration, but it is not merged into main and has not been verified on a physical Android device. Therefore all six engines remain non-selectable in the runtime catalog until implementation and acceptance gates pass.

## Runtime architecture

- Exactly one Android `VpnService` owns the TUN interface.
- Each engine adapter is a transport backend, not a second TUN owner.
- Engine adapter contract: strict config parser, capability declaration, native runtime lifecycle, protected outbound sockets, packet forwarding, counters, cancellation, deterministic teardown.
- TUN file descriptor ownership and socket protection are explicit. No adapter may return success merely because a config parsed or a process started.
- The coordinator transitions to Connected only after engine readiness AND verified traffic through the VPN path AND external egress verification.
- On failure, timeout, malformed config, or lost engine process, fail closed when lockdown is enabled; never silently route through the physical interface.
- A switch between engines must stop and drain the old runtime, establish the new runtime, verify egress, then publish the new active engine. Avoid two simultaneous TUN owners.

## Engine integration order

### 1. WireGuard
Use the maintained official Android tunnel backend as the first packet-forwarding reference implementation. Verify the exact dependency version, transitive native libraries, ABI coverage, license obligations, socket protection, DNS, IPv4/IPv6, MTU, reconnect and teardown.

### 2. AmneziaWG
Integrate a maintained Android-compatible implementation that actually supports Amnezia's obfuscation parameters. A standard WireGuard backend plus a parser for JC/JMIN/JMAX/S1/S2/H1-H4 is not AmneziaWG support. Interoperability with server-side AmneziaWG is mandatory.

### 3. Xray-core
Pin an upstream Xray-core release and supported Android integration. Implement VLESS + REALITY first, then add other profiles only when upstream support and tests exist. The core must bridge packets from the app-owned TUN; URI parsing alone is not a transport.

### 4. sing-box
Integrate the official Android library/runtime (libbox) as a distinct engine adapter. Avoid bundling a second independent TUN owner. Expose only protocols actually enabled in the pinned build. Review GPL and source-distribution obligations before shipping.

### 5. Hysteria 2
Integrate a pinned maintained client runtime over QUIC/UDP. Test networks where UDP is rate-limited, fragmented, or blocked; classify it as unavailable rather than repeatedly retrying when probes show UDP unusable.

### 6. Shadowsocks 2022
Use a maintained implementation of the 2022 methods, not legacy Shadowsocks compatibility code. Verify AEAD method support, replay protection, UDP relay, key handling and TUN integration.

## Iran-focused route selection

- Collect rolling measurements per network identity without storing browsing destinations or payloads.
- Measure DNS reachability, TCP/TLS reachability, UDP/QUIC reachability, packet loss, latency, jitter, throughput, handshake time, reconnect success and stability over time.
- Use a conservative score with stability and packet loss weighted more heavily than peak throughput.
- Apply hysteresis and a cooldown to avoid rapid engine flapping.
- Do not label an engine as blocked from one failed probe. Require repeated failures and compare independent probe paths.
- Keep manual engine selection available. Automatic mode must explain why a route was selected and allow user override.
- Never promise censorship circumvention or universal availability.

## Security baseline

- Use each upstream protocol's reviewed cryptographic suite; do not add a second ad-hoc encryption layer.
- Keep private keys and credentials out of logs, analytics, crash reports, source control and plaintext preferences.
- Store app-managed secrets with Android Keystore-backed encryption where appropriate.
- Validate peer/server identity and TLS certificates; no trust-all callbacks or disabled hostname verification.
- Protect engine sockets from VPN recursion using the actual Android VpnService socket-protection API.
- Enforce DNS routing, IPv6 routing or explicit IPv6 blocking, and lockdown behavior.
- Signed app/core updates only: pinned signing identity, SHA-256, version/ABI checks, rollback protection and atomic install. No executable core downloads until the signed bundle design is reviewed.
- Add SBOM, dependency vulnerability scanning, license inventory and required upstream notices.

## License and supply-chain gate

Before integrating each engine, record the exact upstream repository, immutable release/tag and commit, license expression, transitive native dependencies, notices/source-offer requirements, ABI artifacts, checksum/signature provenance and update policy. Do not infer license compatibility from a project name or a wrapper's license. No binary may be shipped until legal obligations and redistribution rights are confirmed.

## Required validation

Automated: parser fuzzing, malformed input, lifecycle and cancellation tests, socket-protection failure, TUN setup failure, DNS policy, IPv4/IPv6 routing policy, reconnect state machine, dependency/SBOM and license checks.

Physical devices and authorized networks: real handshake, bidirectional traffic, DNS leak, IPv4/IPv6 leak, kill switch/lockdown, network handover, sleep/wake, process death, battery, thermal behavior, long-duration stability and per-engine throughput/latency. Test multiple Iranian mobile and fixed-network providers where lawful and available.

## Release policy

The UI may show an engine as selectable only at `PRODUCTION` readiness. `DEVICE_VERIFIED` requires physical-device acceptance. Debug CI success is not a production release. No APK is to be published until all release gates, existing owner signing-key verification and physical-device tests pass.
