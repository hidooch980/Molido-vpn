# Ten-feature dashboard integration plan

This document is the implementation and acceptance contract for the ten dashboard capabilities. A dashboard label is not proof that a capability is operational.

## Current implementation in this branch

1. **Connection control** — premium status UI is present. WireGuard adapter now fails closed and cannot emit a fake transport-ready event until GoBackend, single-TUN ownership, protected sockets, and packet forwarding are implemented.
2. **Exit IP and location** — the verifier now requires Android's active network to expose `TRANSPORT_VPN`, opens the HTTPS egress probe on that exact `Network`, validates the returned IP literal, and never reports the probe server's IP as the user's IP. Geolocation is not integrated.
3. **Network monitor** — connection latency and engine byte counters are represented. Packet loss, jitter, throughput, and long-window stability still need real probes and device tests.
4. **Security center** — DNS provider and Kill Switch preferences exist. DNS leak, IPv6 leak, lockdown, and process-death behavior are not certified.
5. **Smart routing** — scoring code exists, but the app no longer fabricates latency/loss/stability values. Auto selection rejects routes without real eligible measurements.
6. **Engine manager** — current adapter contracts and partial parsers exist. GoBackend and the remaining production engine runtimes are not integrated or device-verified.
7. **Connection history** — not implemented. Must record only non-secret event metadata with retention and export controls.
8. **Core updates** — not implemented. Must verify signed manifests/artifacts, app signature lineage, ABI, version policy, anti-rollback, and recovery before activation.
9. **AI diagnostics** — not implemented as an AI service. Initial diagnostics must be deterministic, local, explainable, and consent-aware; no secret-bearing logs.
10. **Personalization** — Persian/English and several preferences exist. Theme, notifications, and accessibility settings need completion.

## Data integrity rules

- No sample server profile, endpoint, private key, or synthetic performance score may appear as a connectable option.
- Community subscription content is untrusted input. Parsing is not connectivity verification.
- A route becomes eligible only after real reachability, latency, packet-loss, and stability measurements from the device.
- Exit IP is accepted only from an HTTPS response bound to the Android active VPN `Network`.
- Geolocation must be clearly approximate and must not be inferred from the user's device location.
- A Kill Switch preference is not a successful Kill Switch test.
- CI and unit tests do not constitute Android device acceptance.

## Required release gates

### WireGuard
- Pin and audit the official WireGuard Android library version and license.
- Integrate GoBackend as the sole owner of its TUN interface; do not call `VpnService.Builder.establish()` in parallel for that tunnel.
- Verify the exact upstream manifest/service requirements.
- Pass the real socket-protection callback to GoBackend for every tunnel socket.
- Verify real packet forwarding, IPv4 and IPv6 routes, DNS, public egress IP, reconnect, handover, process death, sleep/wake, and long-duration stability on a physical Android device with a valid reachable profile.

### Remaining modules
- Add source-backed measurements and persistence with explicit privacy/retention rules.
- Add signed update verification and rollback tests before any automatic core update.
- Keep AI remediation advisory unless the user approves a sensitive action.
- Complete OWASP MASVS-oriented security review and device testing.

## Release policy

Do not mark any engine or feature production-ready until its implementation-specific tests pass. Do not publish a release APK until device acceptance and the existing signing-key fingerprint are verified.
