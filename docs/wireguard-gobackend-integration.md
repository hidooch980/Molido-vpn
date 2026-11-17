# WireGuard GoBackend integration

## Implementation

- Dependency: `com.wireguard.android:tunnel:1.0.20260102`, pinned to the Maven Central release and licensed Apache-2.0.
- Runtime: official `GoBackend` / `wireguard-go` userspace implementation.
- TUN ownership: `GoBackend.VpnService` is the only VPN service declared by the app manifest for the WireGuard path. The previous app-owned bare-TUN service is no longer declared or started by the ViewModel.
- Socket protection: the upstream GoBackend protects its native IPv4 and IPv6 UDP sockets through its own VPN service. The generic adapter socket-protection callback is not used by GoBackend; the coordinator now fails closed for adapters that rely on that callback.
- State: backend activation emits `TransportReady` only after `GoBackend.setState(UP)` returns and reports `UP`. Unexpected backend DOWN after startup emits a recoverable failure. The coordinator tears down first, then retries the last validated profile up to three times with bounded backoff; manual disconnect cancels recovery. This is not considered a verified internet connection; the coordinator must still pass the HTTPS egress verifier.
- Validation: the app's bounded config validation is followed by the upstream WireGuard parser before activation.
- WireGuard startup is enabled only in debug builds for internal device acceptance. Release builds fail closed until the full physical-device acceptance matrix is completed and reviewed.
- Non-WireGuard engines remain blocked at the TUN gate until they have complete packet-forwarding backends.

## Important boundaries

- A backend state of UP proves that the userspace tunnel was activated, not that the peer completed a handshake or that the internet is reachable.
- A successful HTTPS egress probe must traverse Android's active VPN Network. Validated physical-network handovers trigger a fresh egress probe; transient failure causes bounded teardown/reconnect and a new verification before Connected is shown. The RFC 8484 client binds app-owned DNS requests to that VPN Network; Android system UDP/53 traffic is not intercepted or converted to DoH. An independent diagnostic runner probes IPv4-only/IPv6-only egress and observes system DNS resolvers through a random bash.ws test query. These diagnostics still require physical-device validation and do not by themselves certify no leaks.
- The Quick Settings tile is intentionally not declared while it still targets the removed legacy service. It must be reimplemented against the shared tunnel controller before being re-enabled.
- No physical-device acceptance has been performed in this integration change.

## Required acceptance on a real Android device

1. Install a build containing the merged manifest and verify Android resolves the GoBackend VPN service.
2. Grant VPN permission and start a valid, reachable WireGuard profile.
3. Confirm the backend reports UP and inspect peer handshake age and transfer counters.
4. Confirm HTTPS public egress IP through the active VPN Network.
5. Run independent IPv4-only and IPv6-only HTTPS probes, observe system DNS resolvers with a unique authoritative DNS query, and separately test app-scoped RFC 8484 DoH. An empty DNS observation is inconclusive, not a clean result. System-wide UDP/53 interception remains outstanding.
6. Test disconnect, reconnect, Wi-Fi/mobile handover (the app now re-verifies egress after validated physical network changes), airplane mode, process death, sleep/wake, and at least a multi-hour soak.
7. Test Android Always-on VPN / lockdown separately; do not infer it from the in-app Kill Switch preference.

## Release gates

Do not mark WireGuard DEVICE_VERIFIED or PRODUCTION until all physical-device acceptance tests pass with a valid user-supplied profile. Do not publish a signed release APK until the existing signing certificate fingerprint is verified.
