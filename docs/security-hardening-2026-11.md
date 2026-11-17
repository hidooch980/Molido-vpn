# MolidoVPN security hardening record

## Changes applied on this branch

- Android manifest explicitly disables cleartext traffic and applies a network security configuration that trusts system CAs only.
- VPN adapters for AmneziaWG, Xray, Hysteria 2 and WARP now fail closed with `ENGINE_RUNTIME_NOT_IMPLEMENTED`; they do not emit a false `TransportReady` event or claim a live tunnel.
- Added unit tests asserting those adapters cannot report readiness before a native transport exists.
- DNS provider selection rejects unknown provider IDs instead of silently falling back to Cloudflare.
- Added an RFC 8484 POST DoH client for app-owned A/AAAA lookups. HTTPS is opened on Android active VPN Network, redirects are disabled, response size and content type are bounded, and DNS transaction/question fields are validated. The VPN builder still configures ordinary DNS IPs; system-wide UDP/53 interception is not implemented.
- DNS builder errors are no longer swallowed silently.
- Connection diagnostics return stable error codes rather than exposing raw exception messages that might contain configuration details.
- WireGuard startup failure attempts to tear down a partially activated backend tunnel before returning a sanitized failure.
- Smart routing rejects negative latency, out-of-range loss/stability, NaN/infinite metrics and blank engine identifiers instead of clamping malformed measurements into eligible routes.
- Added tests for malformed smart-routing measurements.
- Android CI now includes dependency review on pull requests.
- Added scheduled CodeQL analysis for Java/Kotlin.

## Required security verification still outstanding

The following are NOT certified by these source changes or by emulator builds:

- The independent diagnostic runner now probes IPv4-only and IPv6-only HTTPS endpoints and triggers a random system-resolver query observed by the external bash.ws authoritative test service. Results are not a device certification; an empty resolver response is inconclusive, and the third-party service receives the test nonce, observed resolver IPs and request source IP.
- RFC 8484 DoH client is implemented for app-owned A/AAAA lookups and binds HTTPS requests to Android active VPN Network. It does not intercept system-wide UDP/53 traffic; the GoBackend TUN still needs a DNS packet proxy before system-wide DoH can be claimed.
- Android Always-on VPN / lockdown configuration and process-death behavior.
- Peer handshake, actual packet forwarding, socket protection and reconnect/handover tests on a physical phone. Validated physical network changes now trigger a fresh VPN-bound egress check; failed checks tear down the session fail-closed.
- Penetration testing of malformed configuration imports, subscription parsing, local storage, exported components and update delivery.
- Signed update metadata, APK signature verification, digest validation and anti-rollback.
- Full dependency/license/SBOM review and device-specific security testing.
- Physical-device long-duration, battery and thermal testing.

## Release rule

No engine other than the device-accepted WireGuard runtime may be marked production-ready. Do not claim the app is leak-proof or penetration-tested until the outstanding tests have passed and their reports are retained. Do not publish a release APK before physical-device acceptance and verification of the owner's existing signing certificate.
