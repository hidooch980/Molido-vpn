# Roadmap

## Phase 0 — repository baseline
- [x] Establish independent repository and product/security principles.
- [x] Document architecture and engine feasibility gates.
- [x] Add Android Gradle project and CI baseline.

## Phase 1 — fail-closed VPN foundation
- [x] Add engine adapter contract and state machine.
- [x] Integrate the official WireGuard Android tunnel library on the hardening branch.
- [x] Remove fabricated default WireGuard credentials and synthetic server measurements.
- [x] Replace probe-destination-as-egress reporting with HTTPS public-IP verification.
- [ ] Verify dependency resolution, Kotlin/Java API compatibility, and all CI tests.
- [ ] Verify service lifecycle, reconnect, process death, and network handover on physical Android devices.
- [ ] Verify public IPv4 and IPv6 egress, DNS behavior, and lockdown/kill-switch behavior.

## Phase 2 — production WireGuard
- [ ] Add instrumentation tests using a controlled WireGuard endpoint.
- [ ] Add real tunnel statistics and handshake freshness checks.
- [ ] Add endpoint reachability and recovery handling without leaking private profile data.
- [ ] Verify all supported ABIs and native library packaging.
- [ ] Complete privacy, dependency, and license audits.

## Phase 3 — additional engines
- [ ] Xray-core: select exact supported protocols and audit upstream/dependencies.
- [ ] AmneziaWG: verify compatible implementation and server configuration.
- [ ] MASQUE/CONNECT-IP: verify maintained interoperable client stack.
- [ ] WARP: only supported official integration.
- [ ] Psiphon: verify SDK/API and redistribution terms.
- [ ] Tor: verify maintained Android integration, DNS and bridge behavior.
- [ ] SHARD remains research-only until a verifiable protocol and implementation exist.

## Phase 4 — product UI and adaptive policy
- [ ] Premium dashboard with truthful live connection state.
- [ ] Public IP and approximate geolocation from a documented provider.
- [ ] Real server catalogue and measured route ranking; no seeded/fabricated rows.
- [ ] Adaptive selection based on measured latency, loss and stability.
- [ ] Split tunneling, safe update mechanism, and multilingual UI.

## Updates
- [x] Check official GitHub Releases on app launch.
- [x] Automatically download newer APKs and verify GitHub SHA-256 metadata.
- [x] Verify package identity and signing certificate against the installed app before handing off to Android installer.
- [x] Publish signed APKs as GitHub Release assets from the gated release workflow.
- [x] Update bundled WireGuard core together with the signed app release.
- [ ] Add periodic background checks and user-configurable update channels.
- [ ] Add independently signed, rollback-safe core bundles only when an engine supports safe dynamic loading and ABI validation.

## Signing and release
- [ ] Locate and securely verify the previously approved owner keystore.
- [ ] Store signing material only in protected CI secrets or a secure local signing environment.
- [ ] Build and verify a signed release without exposing private key material.
- [ ] Produce SBOM, dependency/license report, vulnerability report, and reproducible-build evidence.

## Release gate
No APK is delivered until all automated and real-device acceptance tests pass. A successful Gradle build or backend UP state alone is not proof of end-to-end VPN traffic.
