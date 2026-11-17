# WireGuard physical-device acceptance matrix

Status: **NOT RUN — release activation remains blocked**.

This document is an execution record for a physical Android phone. Unit tests and CI compilation are not substitutes for these checks. Do not create, upload, or distribute a test APK to perform this work. Use an authorized internal engineering installation path only when separately approved; the only distributable artifact remains the final signed 5.0.0 release.

## Preconditions

- Confirm the device model, Android version, security patch, and app build commit.
- Confirm the WireGuard test profile is authorized and uses a reachable test endpoint.
- Record the expected server public key and endpoint; never record the private key.
- Confirm Android VPN permission is granted and no other VPN app/profile is active.
- Confirm the old release signing certificate fingerprint from the protected signing owner before release work. Do not generate or substitute a key.
- Verify the manifest declares only the upstream GoBackend VPN service and no second app-owned `VpnService.Builder.establish()` path is active.

## Acceptance matrix

| ID | Test | Procedure | Pass criteria | Evidence |
|---|---|---|---|---|
| WG-01 | Backend handshake | Start the WireGuard profile and inspect backend state plus server-side handshake timestamp | GoBackend reaches UP; server records a fresh handshake for the expected peer | Timestamp, peer public-key suffix, backend state |
| WG-02 | Single TUN ownership | Inspect active VPN service and TUN interface lifecycle while connecting, reconnecting, and disconnecting | Exactly one VPN TUN is created by GoBackend; no second establish call/service or duplicate interface appears | Service dump, interface list, lifecycle log |
| WG-03 | Protected transport socket | Verify GoBackend's native tunnel socket is excluded from its own VPN routing | Endpoint transport remains reachable without routing recursion; no unprotected fallback socket | Backend/socket diagnostics |
| WG-04 | Bidirectional packet flow | Transfer known payloads in both directions through the tunnel | Server and client byte counters increase; returned payload hash matches | Captured byte counters and payload hash |
| WG-05 | IPv4 egress | Query a dedicated IPv4-only egress endpoint while connected | Observed public IPv4 belongs to the VPN exit; direct physical egress is not observed | Endpoint result and server-side observation |
| WG-06 | IPv6 egress | Query a dedicated IPv6-only endpoint while connected | IPv6 either exits through the VPN or is unreachable by an explicit fail-closed policy; physical IPv6 egress is never exposed | Endpoint result and server-side observation |
| WG-07 | App-owned DoH A/AAAA | Resolve controlled A and AAAA names using the VPN-bound DoH client | HTTPS request uses the active VPN Network; valid DNS wire response; no redirect or ordinary-network fallback | Resolver logs, request outcome, network identity |
| WG-08 | System DNS leak | Trigger unique authoritative DNS leak-test names from the system resolver path | Resolver observations match the expected VPN resolver path; empty/inconclusive evidence is not a pass | Authoritative resolver report |
| WG-09 | Physical network loss | Disable Wi-Fi/mobile data while tunnel is active | Tunnel is torn down or remains safely blocked; state enters Recovering; no ordinary-network traffic escapes | State timeline and external capture |
| WG-10 | Network handover | Move Wi-Fi to cellular and cellular to Wi-Fi | Tunnel is reverified; if invalid, teardown completes before bounded reconnect | State timeline, handshake, egress |
| WG-11 | Engine failure | Force backend DOWN unexpectedly | Recoverable failure emitted once; old session is closed before reconnect; retries are bounded | Backend state and retry count |
| WG-12 | Teardown retry | Simulate/observe backend shutdown failure then retry disconnect | App never reports Idle while old TUN/session ownership remains; later retry can complete cleanup | State and service/interface snapshots |
| WG-13 | Kill switch / lockdown | Enable Android Always-on VPN and Block connections without VPN in system settings, then interrupt tunnel | Android blocks non-VPN traffic; app UI does not claim OS lockdown unless system setting is verified | Android VPN settings and external capture |
| WG-14 | Reboot / process death | Reboot or force-stop under the approved test procedure | No stale app-owned session is reported; OS VPN policy behaves as configured; reconnect requires safe state reconciliation | Boot log, service state, egress test |

## Result record

For every row record: date/time, device, Android version, app commit, test profile identifier (redacted), network type, result (PASS/FAIL/BLOCKED/INCONCLUSIVE), evidence location, and reviewer.

A test is **PASS** only when its pass criteria and evidence are both present. A failed or inconclusive row blocks release readiness.

## Release gate

- All WG-01 through WG-14 must be reviewed. Any test not applicable requires a written rationale and reviewer approval.
- CI unit tests, lint, and source compilation must pass on the exact candidate commit.
- No test APK is built or distributed.
- Only after acceptance may the release pipeline create final version 5.0.0, verify it is signed by the existing approved key, verify the certificate fingerprint and artifact digest, and publish it with explicit release approval.
- The five other engines remain disabled until WireGuard acceptance is complete. Each engine then receives its own transport, socket protection, TUN integration, DNS/IP-family, failure-recovery, and physical-device acceptance record before activation.
