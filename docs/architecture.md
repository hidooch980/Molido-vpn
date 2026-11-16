# MolidoVPN AI — architecture

## Runtime boundaries

```text
Compose UI
  └─ ViewModel / use cases
      └─ ConnectionCoordinator (Kotlin)
          ├─ Policy + route scorer
          ├─ EngineAdapter interface
          │   ├─ WireGuard adapter
          │   ├─ Xray adapter
          │   ├─ AmneziaWG adapter
          │   └─ optional engines behind capability gates
          └─ Android VpnService
              ├─ TUN interface and routes
              ├─ DNS policy
              ├─ foreground notification
              └─ lifecycle / lockdown integration

Optional Rust core:
  deterministic scoring, parsing, and isolated performance-critical code.
```

## Key design decisions
- Android `VpnService` is the single owner of the device TUN interface. Engines must integrate through a documented adapter boundary; competing engines must not independently race to own the VPN interface.
- One active engine at a time for the initial release. Multi-hop chaining is a later, separately threat-modeled feature.
- The coordinator uses an explicit state machine: Idle → Preparing → Connecting → Verifying → Connected; failures move to Recovering or Failed. Disconnect always tears down routes and descriptors.
- Engine adapters expose capabilities (IPv4, IPv6, UDP, DNS mode, per-app routing, MTU, proxy/TUN mode), not just a display name.
- A route is eligible only when its configuration is valid, its upstream service is reachable, and its capabilities satisfy the user's policy.
- Adaptive selection first uses deterministic measurements and bounded scoring. ML must not override privacy, security, or user constraints.
- Verification checks actual traffic through the TUN path, DNS resolution path, external IPv4/IPv6 egress, and recovery behavior. UI must distinguish “handshake” from “traffic verified”.
- Country and IP labels are obtained from a documented IP metadata provider and marked approximate.

## Engine adapter contract (conceptual)
Each adapter must provide:
- stable engine ID and version;
- license and notices metadata;
- capability declaration;
- configuration validation without logging secrets;
- start/stop lifecycle bound to the VPN service;
- health and structured error events;
- traffic counters and verification hooks where available.

## Privacy
Keep route measurements local by default. Do not collect domains, payloads, or browsing history. Any optional diagnostics upload requires clear opt-in and redaction.
