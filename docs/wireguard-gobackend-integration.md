# WireGuard GoBackend integration

## Implementation

- Dependency: `com.wireguard.android:tunnel:1.0.20260102`, pinned to the Maven Central release and licensed Apache-2.0.
- Runtime: official `GoBackend` / `wireguard-go` userspace implementation.
- TUN ownership: `GoBackend.VpnService` is the only VPN service declared by the app manifest for the WireGuard path. The previous app-owned bare-TUN service is no longer declared or started by the ViewModel.
- Socket protection: the upstream GoBackend protects its native IPv4 and IPv6 UDP sockets through its own VPN service. The generic adapter socket-protection callback is not used by GoBackend; the coordinator now fails closed for adapters that rely on that callback.
- State: backend activation emits `TransportReady` only after `GoBackend.setState(UP)` returns and reports `UP`. This is not considered a verified internet connection; the coordinator must still pass the HTTPS egress verifier.
- Validation: the app's bounded config validation is followed by the upstream WireGuard parser before activation.
- Non-WireGuard engines remain blocked at the TUN gate until they have complete packet-forwarding backends.

## Important boundaries

- A backend state of UP proves that the userspace tunnel was activated, not that the peer completed a handshake or that the internet is reachable.
- A successful HTTPS egress probe must traverse Android's active VPN Network. DNS and IPv6 leak behavior still require dedicated physical-device tests.
- The Quick Settings tile is intentionally not declared while it still targets the removed legacy service. It must be reimplemented against the shared tunnel controller before being re-enabled.
- No physical-device acceptance has been performed in this integration change.

## Required acceptance on a real Android device

1. Install a build containing the merged manifest and verify Android resolves the GoBackend VPN service.
2. Grant VPN permission and start a valid, reachable WireGuard profile.
3. Confirm the backend reports UP and inspect peer handshake age and transfer counters.
4. Confirm HTTPS public egress IP through the active VPN Network.
5. Verify IPv4, IPv6, DNS, and DNS-over-HTTPS behavior; verify no fallback leaks on tunnel loss.
6. Test disconnect, reconnect, Wi-Fi/mobile handover, airplane mode, process death, sleep/wake, and at least a multi-hour soak.
7. Test Android Always-on VPN / lockdown separately; do not infer it from the in-app Kill Switch preference.

## Release gates

Do not mark WireGuard DEVICE_VERIFIED or PRODUCTION until all physical-device acceptance tests pass with a valid user-supplied profile. Do not publish a signed release APK until the existing signing certificate fingerprint is verified.
