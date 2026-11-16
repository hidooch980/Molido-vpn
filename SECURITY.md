# Security policy

## Reporting
Do not disclose exploitable vulnerabilities in public issues. Use GitHub's private vulnerability reporting feature if enabled for this repository; otherwise contact the repository maintainer privately.

## Security requirements
- No private signing material, passwords, API tokens, or long-lived shared secrets in source code, Git history, BuildConfig, APK resources, or logs.
- Pin and review third-party dependencies; generate an SBOM for release candidates.
- Verify upstream signatures/checksums where available.
- Keep Android traffic routing and DNS behavior explicit and testable.
- Do not claim DNS/IP leak protection without IPv4, IPv6, DNS, reconnect, and failure-path tests.
- Kill switch must use Android-supported lockdown semantics where available and clearly explain platform limitations.
- Store user credentials/configuration using Android Keystore-backed encryption where appropriate.
- Minimize telemetry; no traffic contents or browsing history collection.
- Release builds must be signed with the established owner-controlled release key. Never generate or disclose a replacement key without explicit need and owner approval.

## Scope
Only test systems and servers for which you have authorization. Engine adapters must use official protocols and documented APIs.
