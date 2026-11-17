# MolidoVPN AI 5.0.0 — Final Release Gates

Last reviewed: 2026-11-17

This file is a release-control record, not a claim that the product is complete.

## Latest branch verification — 2026-11-17

- Feature branch: `feature/wireguard-gobackend-integration-2026-11`.
- Verified candidate before this documentation refresh: `32eba56b94b3e7d66893e82ffa63a732f8c37bea`.
- Android CI run `44075393031`: **success** on that exact candidate.
- CodeQL run `44075393112`: **success** on that exact candidate.
- Android CI included unit tests, lint, instrumentation-test source compilation (no APK), and dependency review.
- The branch was observed 0 commits behind `main`, 234 commits ahead, and GitHub reported it mergeable.
- This documentation update changes the candidate commit. CI and CodeQL must run again on the resulting new head before any merge decision.
- Repository visibility is intentionally **public**, per the owner's latest explicit decision. The historic signing-key exposure remains unresolved; public history still exposes the historical signing material.
- No APK was assembled, installed, uploaded, or distributed.

## Verified repository-side work

- Android CI and CodeQL passed on the previously reviewed candidate commit `6370bc6100158d074f77704c1a70c31b230164c3`.
- Country label resolution is being hardened to accept explicit, whole-token country labels only. It does not establish physical server geolocation.
- The default free-server catalog intentionally returns no synthetic server entries.
- Profiles without measured route data remain ineligible for Smart Routing.
- The updater now withholds direct APK URLs from unsigned GitHub release metadata. It can open the official release page for manual inspection only; it does not verify or install an update.
- No Debug, Beta, test, or other experimental APK is to be assembled, uploaded, or distributed.

## Hard blockers — do not bypass

### 1. Physical WireGuard acceptance

WG-01 through WG-14 are **NOT RUN / NOT ACCEPTED** on a real Android phone. The full evidence matrix is in [WIREGUARD_DEVICE_ACCEPTANCE_MATRIX.md](WIREGUARD_DEVICE_ACCEPTANCE_MATRIX.md).

Required evidence includes GoBackend handshake, one TUN owner, protected transport socket, bidirectional payload transfer, VPN-bound egress, IPv4/IPv6 behavior, DNS observations, physical network loss/handover, cleanup retry, Android Always-on/Block-without-VPN, reboot/process death, and soak stability.

CI, unit tests, source compilation, and a backend UP state do not replace these tests. An empty DNS observation is inconclusive. App-process DNS observations do not prove system-wide DNS interception.

### 2. Signing key exposure and public repository

The owner explicitly chose to keep this repository **public**. The prior release keystore and signing credentials appeared in public Git history. The old signing key must be treated as compromised and must never sign a new release.

Required remediation:
- Keep the repository public unless the owner explicitly changes this decision.
- Verify the authorized replacement signing identity using an owner-held, trusted offline keystore certificate fingerprint. Do not expose keystore bytes, passwords, or private key material.
- Remove/rotate exposed signing credentials and any other credentials that reused them.
- Do not generate or substitute a new key without explicit owner authorization.
- Do not rewrite Git history or force-push until a complete offline backup exists and the owner explicitly approves that destructive history operation.
- Understand that deleting old Release assets and tags does not erase copies of the exposed key already present in public Git history.

Until the signing identity is independently verified and attested, production release remains blocked.

### 3. Five other VPN engines

The five engines remain disabled until WireGuard physical acceptance is complete. After that gate, integrate and test each engine separately:

- AmneziaWG
- Xray-core (initially VLESS + REALITY)
- sing-box (libbox Android)
- Hysteria 2
- Shadowsocks 2022

Each requires a pinned maintained runtime, license/SBOM review, single-TUN ownership, protected sockets, real bidirectional packet forwarding, DNS and IPv4/IPv6 handling, lifecycle/recovery tests, and its own physical-device acceptance. Parser success or UI presence is not runtime integration.

### 4. Secure automatic updates

Automatic installation remains disabled. Before enabling it, implement and independently test:
- Signed update manifest and pinned public verification key.
- APK signer-certificate identity verification.
- SHA-256 artifact digest verification.
- Version/ABI/OS compatibility allowlist.
- Anti-rollback and replay protection.
- HTTPS download with bounded size and safe temporary storage.
- Atomic staging, failure cleanup, and rollback.
- Android package-installer handoff with explicit user/system approval.
- A documented key-rotation and update-continuity strategy.

GitHub Release metadata and a GitHub download URL are not cryptographic authenticity proofs.

## Merge and release policy

- Keep PR #8 Draft and unmerged while the WireGuard physical acceptance or signing-key blocker remains.
- Do not force merge, bypass branch protection, or mark the PR ready based on CI alone.
- Re-run Android CI and CodeQL on the exact final candidate commit after every code change.
- Do not create any experimental APK.
- The first permitted production release is MolidoVPN AI 5.0.0. Subsequent releases may be 5.x or later; versions below 5.0.0 are forbidden by the release workflow.
- Build and distribute only a final signed production artifact after every release gate is reviewed and explicitly approved.
- Existing public Releases/tags below 5.0.0 still require deletion; the connected GitHub tool currently cannot perform those delete operations.

## Current truthful status

The repository contains meaningful source integration and automated checks, but the application is **not release-ready**. The physical WireGuard tests, signing-key remediation, five independent engine runtimes, and cryptographically secure updater remain incomplete or blocked. No production readiness or leak-proof claim is authorized.
