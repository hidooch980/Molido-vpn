# MolidoVPN AI — architecture

## Runtime boundaries

    Compose UI
      └─ ViewModel / use cases
          └─ ConnectionCoordinator
              ├─ deterministic route policy
              ├─ EngineAdapter
              │   └─ WireGuardEngineAdapter
              │       └─ official WireGuard GoBackend
              │           ├─ wireguard-go userspace transport
              │           └─ library-owned Android VpnService / TUN
              └─ HTTPS public-egress verifier

Future engines (Xray, AmneziaWG, MASQUE, WARP, Psiphon, Tor) are gated by separate feasibility, license and security reviews.

## Connection invariants
- WireGuard's maintained Android backend owns its VPN service and TUN descriptor; Molido must not establish a competing empty TUN.
- The profile must parse successfully and contain an interface address and at least one peer.
- A backend UP state means the tunnel engine activated; it is not proof that traffic reaches the public internet.
- The coordinator enters Connected only after an HTTPS IP-echo request succeeds and returns a literal public egress IP.
- A failed verification tears down the engine session and clears the displayed egress IP.
- No fallback default routes, synthetic server inventory, mock credentials, or hard-coded public IPs are permitted in production code.
- Engine adapters must not log private keys, credentials, or complete profiles.

## Privacy and geolocation
- Public IP is obtained from a configured HTTPS IP-echo service and should be shown only after successful verification.
- Geolocation is approximate and must be sourced separately from a documented provider. Never infer a country from a VPN server label.
- Route measurements remain local by default. Diagnostics upload requires explicit opt-in and redaction.

## Verification still required
Physical Android devices must verify connection, disconnection, reconnect, network changes, process death, IPv4/IPv6 egress, DNS leakage, and Android lockdown/kill-switch behavior. CI unit tests cannot replace these tests.
