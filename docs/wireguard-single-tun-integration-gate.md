# WireGuard single-TUN integration gate

## Decision

Use the maintained WireGuard Android `GoBackend` as the only owner of the WireGuard TUN. Do not call `VpnService.Builder.establish()` from `MolidoVpnService` for a GoBackend-managed session. Do not create a second TUN or run a userspace packet loop alongside GoBackend.

This document is an integration gate, not a claim that WireGuard is operational.

## Verified source-level findings

- The current `feature/iran-six-engine-runtime` service creates a TUN with its own `Builder().establish()` in `MolidoVpnService.establishTunInterface()`.
- The earlier WireGuard adapter on `hardening/real-tunnel-no-false-connected` creates `GoBackend(context)` and calls `backend.setState(..., UP, config)`.
- Upstream `GoBackend` starts and waits for its own nested service class, `GoBackend.VpnService`, then obtains that service's Builder and establishes the TUN itself. It also calls `VpnService.protect()` for its WireGuard sockets.
- Therefore the earlier adapter cannot safely be copied into the current service unchanged. Its required service declaration and lifecycle ownership must be explicitly wired and tested.

## Required implementation sequence

1. **Manifest and service ownership**
   - Declare `com.wireguard.android.backend.GoBackend$VpnService` as the VPN service for GoBackend, with `android.permission.BIND_VPN_SERVICE` and the `android.net.VpnService` intent filter.
   - Remove VPN-service ownership from `MolidoVpnService`; convert it to a non-VpnService orchestration/foreground service, or otherwise prove a single service architecture with one TUN owner.
   - Ensure the app does not register two competing VPN services or call `Builder.establish()` outside the selected backend.
   - Verify the merged manifest from the actual release variant.

2. **Adapter contract**
   - Keep one adapter contract across all engines. Preserve an explicit socket-protection capability for engines that own sockets; for GoBackend, document and verify its internal socket protection rather than passing a fake `{ true }` callback.
   - Serialize backend state changes and make connect/disconnect idempotent.
   - Treat `Tunnel.State.UP` as backend state only, not proof of a successful handshake or internet egress.
   - Report `TransportReady` only after the backend has started; report user-visible Connected only after real HTTPS egress verification.

3. **Configuration and security**
   - Parse the profile using WireGuard's official `Config.parse`; reject empty peers, absent interface addresses, invalid endpoints and unsupported configuration.
   - Keep private keys out of logs, diagnostics, crash reports and analytics.
   - Verify DNS routing, IPv4/IPv6 behavior, allowed/disallowed apps, MTU and fail-closed teardown.
   - Confirm dependency version, native ABI packaging, transitive licenses and notices against the exact resolved artifact.

4. **Automated validation**
   - Add tests for backend state transitions, duplicate connect, disconnect during connect, backend exceptions, service startup timeout, permission denial and process/service destruction.
   - Add Android instrumentation tests that install the merged manifest and exercise VPN permission and actual backend lifecycle.
   - Unit tests and a successful debug build are not evidence of packet forwarding.

5. **Physical-device acceptance**
   - On each supported Android version, verify handshake age, byte counters and a controlled HTTPS egress-IP change.
   - Verify DNS and IPv6 leak behavior, Kill Switch/lockdown, Wi-Fi-to-cellular handover, reconnect, sleep/wake, process death and long-duration stability.
   - Do not mark WireGuard `DEVICE_VERIFIED` or `PRODUCTION` until all required results are attached to the release review.

## Current release status

- WireGuard remains non-selectable in `EngineRuntimeCatalog`.
- The earlier WireGuard PR is not mergeable into the current base and does not establish physical-device acceptance.
- The six-engine catalog is a readiness gate; it does not implement the other five engines.
- No signed final APK may be published until device acceptance and the previously approved signing certificate are verified.

## References

- Upstream GoBackend source: https://github.com/WireGuard/wireguard-android/blob/master/tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java
- Earlier integration PR: https://github.com/hidooch980/Molido-vpn/pull/1
- Current six-engine architecture PR: https://github.com/hidooch980/Molido-vpn/pull/2
