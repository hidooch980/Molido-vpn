# WireGuard integration audit — current six-engine branch

Audit baseline:
- Current feature branch commit: 2a7a1b46b4028f253f4a218d1a8e193179d5dcd6
- Earlier WireGuard PR head: 5e5acb345213518a8de0a62605f90ac210efeeb1
- Earlier PR: https://github.com/hidooch980/Molido-vpn/pull/1
- Six-engine gate PR: https://github.com/hidooch980/Molido-vpn/pull/2
- CI run for the current six-engine commit: 43970370367 (success)

## Findings

1. The earlier PR adds the official Android WireGuard tunnel dependency and a GoBackend adapter. This is materially better than the current parser-only placeholder.
2. The earlier adapter has a different EngineAdapter.connect contract from the current branch: the old version does not accept the protectSocket callback, while the current contract does.
3. The earlier adapter uses GoBackend, which owns Android VPN/TUN lifecycle and protects its own transport sockets. The current MolidoVpnService independently calls VpnService.Builder.establish(). These two ownership models must not be combined into a competing/double-TUN design.
4. The earlier PR's coordinator and verifier changes are not a clean cherry-pick: its diagnostics and coordinator contracts differ from the current branch. Its HTTPS egress verifier is useful, but must be integrated against the current coordinator and must fail closed on missing/invalid egress IP.
5. The earlier PR's manifest does not explicitly declare the app's MolidoVpnService in the fetched file. The merged manifest and GoBackend service declaration must be inspected from the actual dependency/AAR during build; do not assume manifest merging is correct.
6. The earlier PR's JVM test only checks empty-profile parsing. It does not prove native backend activation, packet forwarding, socket protection, DNS/IPv6 behavior, reconnect, or lockdown.
7. PR #1 currently reports mergeable=false against main and is based on an older main commit. Do not merge it directly into the six-engine branch.

## Required safe port

- Keep one and only one Android VPN/TUN owner. Prefer the official GoBackend ownership model for WireGuard; do not also establish a second TUN in MolidoVpnService.
- Refactor the app-level coordinator/service boundary so it delegates start/stop to one backend and has a single lifecycle owner.
- Preserve the six-engine readiness gate: WireGuard remains non-selectable until integration and device verification pass.
- Use the current EngineAdapter contract consistently. Socket protection must be implemented by the actual backend; never pass a lambda that unconditionally returns true.
- Confirm merged manifest, native ABIs, dependency provenance, Apache-2.0 notices, and transitive dependency licenses.
- Add Android instrumentation tests for permission flow, backend UP/DOWN, real egress traffic, IPv4/IPv6, DNS leak, reconnect, network handover, process death, and lockdown.
- Only then promote WireGuard readiness; no other engine is implicitly enabled.

## Release truth

The successful CI run validates JVM unit tests and a debug build only. It does not establish a working VPN tunnel. No production APK or physical-device verification is claimed by this audit.
