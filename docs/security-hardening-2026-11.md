# MolidoVPN security hardening record

## Changes applied on this branch

- Android manifest explicitly disables cleartext traffic and applies a network security configuration that trusts system CAs only.
- VPN adapters for AmneziaWG, Xray, Hysteria 2 and WARP now fail closed with `ENGINE_RUNTIME_NOT_IMPLEMENTED`; they do not emit a false `TransportReady` event or claim a live tunnel.
- Added unit tests asserting those adapters cannot report readiness before a native transport exists.
- DNS provider selection rejects unknown provider IDs instead of silently falling back to Cloudflare.
- DNS labels no longer claim that ordinary resolver IP configuration is DNS-over-HTTPS. The current builder configures resolver IP addresses only; it is not a DoH client.
- DNS builder errors are no longer swallowed silently.
- Connection diagnostics return stable error codes rather than exposing raw exception messages that might contain configuration details.
- WireGuard startup failure attempts to tear down a partially activated backend tunnel before returning a sanitized failure.
- Smart routing rejects negative latency, out-of-range loss/stability, NaN/infinite metrics and blank engine identifiers instead of clamping malformed measurements into eligible routes.
- Added tests for malformed smart-routing measurements.
- Android CI now includes dependency review on pull requests.
- Added scheduled CodeQL analysis for Java/Kotlin.

## Required security verification still outstanding

The following are NOT certified by these source changes or by emulator builds:

- Real-device IPv4, IPv6 and DNS leak testing under normal operation and tunnel loss.
- RFC 8484 DoH client is implemented for app-owned A/AAAA lookups and binds HTTPS requests to Android active VPN Network. It does not intercept system-wide UDP/53 traffic; the GoBackend TUN still needs a DNS packet proxy before system-wide DoH can be claimed.
- Android Always-on VPN / lockdown configuration and process-death behavior.
- Peer handshake, actual packet forwarding, socket protection and reconnect/handover tests on a physical phone.
- Penetration testing of malformed configuration imports, subscription parsing, local storage, exported components and update delivery.
- Signed update metadata, APK signature verification, digest validation and anti-rollback.
- Full dependency/license/SBOM review and device-specific security testing.
- Physical-device long-duration, battery and thermal testing.

## Release rule

No engine other than the device-accepted WireGuard runtime may be marked production-ready. Do not claim the app is leak-proof or penetration-tested until the outstanding tests have passed and their reports are retained. Do not publish a release APK before physical-device acceptance and verification of the owner's existing signing certificate.
