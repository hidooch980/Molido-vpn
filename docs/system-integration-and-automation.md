# MolidoVPN AI — system integration and automation contract

## Integration baseline

This branch is the consolidation line for the current dashboard work and the official WireGuard runtime. The ten-feature dashboard UI is ported from the earlier dashboard branch; overlapping runtime files are taken from this branch so the older branch cannot overwrite WireGuard or fail-closed fixes.

### Runtime boundaries

- WireGuard uses the pinned official `com.wireguard.android:tunnel:1.0.20260102` library and `GoBackend`.
- `GoBackend.VpnService` is the only VPN service declared for the WireGuard path and owns the TUN.
- The app does not create a competing bare TUN. The old Quick Settings tile remains disabled until it is wired to the shared tunnel lifecycle.
- WireGuard activation is debug-only until physical-device acceptance is signed off. Release builds fail closed.
- A backend state of UP is not proof of peer handshake or internet egress. The app must pass the HTTPS probe bound to Android's active VPN Network before showing verified connectivity.
- Other engines remain unavailable until each has a pinned upstream runtime, license audit, real packet forwarding, protected sockets, and device acceptance.

## Ten dashboard capabilities

| Capability | Current state | Remaining acceptance |
|---|---|---|
| Smart connection/status | UI integrated; WireGuard backend in debug path | Real device session, peer handshake and egress |
| Exit IP/location | VPN-bound HTTPS IP probe | Trusted approximate geolocation source; no false location |
| Network monitor | Basic latency and byte counters | Real packet loss, jitter, throughput, long-window stability |
| Security center | Preferences and fail-closed gates | DNS/IPv6 leak, lockdown, process death and handover tests |
| AI smart routing | Scoring engine rejects missing measurements | Real multi-route probes; deterministic fallback |
| VPN engine manager | Six-engine catalog/adapter contracts | Five additional native runtimes and per-engine device tests |
| Connection history | UI placeholder only | Non-secret event store, retention and export controls |
| Core updates | Manual GitHub release-page flow | Signed metadata, artifact digest, signature lineage and anti-rollback |
| AI diagnostics | Not production implemented | Explainable local diagnostics, consent and redacted logs |
| Personalization | Persian/English and basic preferences | Finish theme, accessibility and notification controls |

A UI item or catalog entry is never evidence that its feature is operational.

## Credential storage

- Active VPN profiles are encrypted with AES-256-GCM.
- The AES key is generated and held by Android Keystore; raw key material is not stored in preferences or source control.
- Legacy Base64-only profile values are migrated to encrypted storage on first successful read.
- Corrupt ciphertext, invalidated keys, or decryption errors fail closed and clear the unreadable profile; the app never falls back to displaying an encoded value as plaintext.
- Profile values, private keys, subscription credentials, and tokens must never be written to logs or crash reports.

## Automated quality and repair loop

1. Every pull request and push to main runs Android unit tests.
2. Android lint runs even when unit tests fail, unless the workflow is cancelled.
3. Android instrumentation test sources are compile-checked only; CI does not assemble, install, upload, or distribute test APKs. Physical Keystore and VPN behavior require device acceptance.
4. Test and lint reports are uploaded for diagnosis, including failed runs.
5. CI concurrency cancels obsolete runs for the same ref.
6. Dependabot opens weekly Gradle and GitHub Actions update PRs. Dependency updates still require CI and review.
7. A failing test is not automatically patched with guessed code. The failure report identifies the test and log; a targeted fix must be committed and the full checks rerun. Only deterministic formatting or dependency updates should be automated without human review.

## Security and release gates

- Do not auto-merge code changes or auto-publish an APK from a green unit-test run.
- Do not claim Kill Switch, DNS protection, IPv6 protection, or location accuracy before device tests.
- Validate TLS hostname/certificate, update signature, artifact digest, ABI, version and rollback policy before enabling automatic updates.
- Do not mark any engine DEVICE_VERIFIED or PRODUCTION until the physical-device matrix passes.
- Release signing must use the owner's verified existing key through protected GitHub secrets. Never put keystore bytes or passwords in source files, workflow logs, or chat.
- Release APK requires physical Android acceptance, signature fingerprint match, license/notice review, and a clean release build.

## Physical-device acceptance matrix

With a valid, reachable profile on a real Android phone:
1. Grant VPN permission and start the GoBackend tunnel.
2. Confirm backend UP, peer handshake freshness, and transfer counters.
3. Confirm public egress IP using the VPN-bound HTTPS probe.
4. Verify IPv4, IPv6, DNS and DNS-over-HTTPS; confirm no fallback leaks.
5. Disconnect and test fail-closed behavior / Android Always-on lockdown separately.
6. Test reconnect, Wi-Fi/mobile handover, airplane mode, sleep/wake and process death.
7. Run a multi-hour soak and inspect battery, thermal and stability behavior.

## Current non-goals

- No fabricated free VPN servers or fake performance metrics.
- No AI agent is allowed to silently change firewall, routing, DNS, credentials, or release settings.
- No automatic release or production engine enablement based only on CI.
