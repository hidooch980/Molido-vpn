# Engine feasibility and release gates

| Engine | Intended role | Integration assessment | Release gate |
|---|---|---|---|
| WireGuard | Fast modern tunnel | Preferred first engine; use official/maintained Android-compatible implementation | Verify upstream license, ABI packaging, key handling, IPv4/IPv6, DNS and reconnect |
| Xray-core | Flexible proxy/tunnel family | Candidate; select exact supported protocols and maintained Android integration | Pin upstream version, audit transitive dependencies, confirm TUN routing and license notices |
| AmneziaWG | WireGuard-derived obfuscation variant | Separate compatibility profile; not interchangeable with ordinary WireGuard peers | Confirm client implementation and server configuration compatibility; document upstream changes |
| MASQUE / CONNECT-IP | HTTP/3-based IP proxy/tunnel | Research/integration candidate; support depends on a maintained CONNECT-IP client stack | Protocol interoperability, QUIC behavior, proxy/TUN integration, licensing and mobile battery tests |
| Cloudflare WARP | Managed network tunnel | Do not assume a public reusable SDK or unrestricted API exists | Use only supported official integration and terms; no credential/API reverse engineering |
| Psiphon | Circumvention network | Candidate subject to upstream SDK/API and distribution terms | Confirm supported embedding model, license, service availability and privacy behavior |
| Tor | Anonymity network | Candidate; requires careful DNS and app-routing design | Use maintained Tor implementation; test bridges, DNS, stream isolation and platform constraints |
| SHARD | Proposed multi-node path | Research only; the name alone does not identify a standard interoperable engine | Require a concrete protocol specification, maintained source, license, threat model and test vectors |

## Status vocabulary
- Planned: no implementation claim.
- Research: feasibility or licensing is being investigated.
- Integrated: adapter compiles and passes unit/integration tests.
- Device-verified: real-device traffic, DNS, IPv6 and recovery tests pass.
- Production: release security, license, performance and regression gates pass.

Do not label an engine “available” based only on a UI entry, binary inclusion, successful process start, or handshake.
