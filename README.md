# MolidoVPN AI

Independent Android VPN client focused on reliable, privacy-preserving multi-engine connectivity.

> Status: architecture baseline. No VPN engine is advertised as operational until its adapter, licensing, configuration, device tests, and traffic-path verification are complete.

## Product goals
- Native Android app using Kotlin and Jetpack Compose.
- Android `VpnService` owns the device VPN interface and lifecycle.
- Rust core for policy, health scoring, and engine orchestration where it provides a measurable benefit.
- Pluggable engine adapters with explicit capability and license metadata.
- Adaptive route selection based on measured reachability, latency, loss, stability, and user policy.
- DNS protection, IPv4/IPv6 leak controls, kill switch guidance, split tunneling, and transparent connection diagnostics.
- Signed releases and safe in-app update checks; signing secrets never enter source control.

## Repository principles
1. Keep this repository independent from the legacy MolidoVPN Android repository.
2. Reuse upstream engines only under compatible licenses and with required notices/source offers.
3. Never embed private API keys, signing keys, passwords, or shared HMAC secrets in the APK.
4. Never report a tunnel as connected until traffic is verified through the VPN interface and external egress checks pass.
5. Fail closed when the user enables lockdown/kill-switch behavior and the tunnel cannot be maintained.
6. Treat country/IP geolocation as best-effort metadata, not proof of physical server location.

## Supported Engine Families
- **Cloudflare WARP (Free)**: Zero-config auto-provisioning with global Anycast endpoint.
- **WireGuard**: Official protocol support and strict offline `.conf` parser.
- **AmneziaWG**: Anti-DPI WireGuard obfuscation (`Jc`, `Jmin`, `Jmax`, `S1`, `S2`, `H1`-`H4`).
- **Xray-core**: VLESS Reality, Trojan, and Shadowsocks URI importer.

## Features
- **Deterministic Route Scoring**: Real-time evaluation of ping, packet loss, stability, and priority.
- **Out-of-the-box Free Servers**: Preloaded community servers and auto-provisioned WARP identity.
- **Subscription & Clipboard Importer**: One-click import for remote subscription links and URIs.
- **Kill Switch & Lockdown**: Strict leak prevention with `setBlocking(true)` and Android settings integration.
- **Encrypted DNS (DoH)**: Built-in anti-spoofing presets for Cloudflare, Google, Quad9, and AdGuard.
- **Split Tunneling**: Per-app VPN routing.
- **Network Handover**: Resilient Wi-Fi <-> Cellular auto-detection.

## Documentation
- [Architecture](docs/architecture.md)
- [Engine matrix](docs/engine-matrix.md)
- [Security model](SECURITY.md)
- [Roadmap](ROADMAP.md)

## Build & Test
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug
```

## Build status
Phase 1 foundation complete: Android `VpnService` lifecycle, foreground notification, TUN configuration, `ConnectionCoordinator`, traffic verification pipeline, and initial WireGuard engine parser/adapter are implemented and tested. See [Roadmap](ROADMAP.md).
