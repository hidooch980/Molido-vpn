# MolidoVPN AI

Independent Android VPN client focused on reliable, privacy-preserving multi-engine connectivity.

> Current status: WireGuard's official Android tunnel backend is wired into the hardening branch. This is an integration change, not a device-verified release. No APK is being distributed from this work branch.

## Product goals
- Native Android app using Kotlin and Jetpack Compose.
- The official WireGuard Android tunnel library provides the first real userspace transport.
- Engine adapters have explicit capability and lifecycle contracts.
- The UI reports Connected only after the backend starts and an HTTPS public-egress IP check succeeds.
- DNS protection, IPv4/IPv6 leak controls, kill-switch behavior, split tunneling, and diagnostics must be verified on real devices before production.
- Country/IP geolocation is approximate metadata and must never be fabricated.

## Security and release principles
1. Never report a VPN connection from TUN creation or a UI event alone.
2. Never ship placeholder profiles, synthetic server inventories, mock IPs, or simulated route measurements as live data.
3. Keep signing keys and credentials outside source control. Reuse the existing owner keystore only after secure verification.
4. Never silently fall back to an unencrypted/default TUN after profile parsing fails.
5. A production engine requires pinned upstream code, license notices, automated tests, and Android device verification.
6. No release APK until all acceptance gates pass.

## Engine status
- WireGuard: official com.wireguard.android:tunnel dependency integrated on the hardening branch; real device verification remains pending.
- Xray-core, AmneziaWG, MASQUE/CONNECT-IP, WARP, Psiphon, Tor: not integrated; each requires separate feasibility, license, and security review.
- SHARD: research only until a concrete maintained implementation and protocol specification are available.

## Build and validation
Run ./gradlew testDebugUnitTest, ./gradlew lint, and ./gradlew assembleDebug.

Debug builds are CI validation artifacts only and are not product releases.

## Release gate
A signed release requires: successful CI, dependency/license review, SBOM and vulnerability review, real Android connect/disconnect/reconnect tests, public IPv4/IPv6 egress checks, DNS leak tests, kill-switch/lockdown tests, network handover and process-death recovery, privacy review, and verification of the owner's existing signing key.
