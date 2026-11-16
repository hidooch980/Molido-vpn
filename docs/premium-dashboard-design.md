# MolidoVPN AI — Premium Dashboard UI

## Visual direction
- Palette: near-black teal background (`#07131A`), deep teal surfaces (`#10242D`), turquoise primary (`#35D6CD`), muted blue-green secondary text, and amber for pending verification.
- Surface treatment: rounded cards, subtle teal borders, restrained glow accents, clear typography, and high-contrast connection status.
- Navigation: expandable Material 3 modal side drawer. Implemented destinations are Dashboard, Servers & Profiles, and Security & Settings.
- Planned destinations are shown as non-interactive development entries until their screens and data sources exist.

## Dashboard information hierarchy
1. Protection status and connect/disconnect action.
2. Exit IP and server location, intentionally unavailable until the active tunnel's external egress is verified.
3. Network performance from existing diagnostics; absent measurements render as an em dash, never as fabricated zeroes.
4. DNS, IPv6, and Kill Switch protection status. A configured setting is not represented as a successful leak test.
5. Engine selection remains connected to the existing ViewModel and readiness gates.

## Truth and safety rules
- UI state must follow the VPN state machine; no decorative animation may imply a connected tunnel.
- Exit IP and geolocation must be populated only from a verified request routed through the active VPN.
- Latency and byte counters are shown only when supplied by diagnostics and relevant to a verified connection.
- DNS/IPv6/Kill Switch labels distinguish configuration from successful device-level verification.
- This dashboard work does not implement or certify WireGuard packet forwarding, GoBackend ownership, socket protection, DNS leak prevention, IPv6 routing, or device acceptance.

## Localization and accessibility
- Existing Persian/English language selection is retained.
- Drawer items include text labels and icons; planned modules are visibly marked as in development.
- Touch targets use Material 3 navigation controls and existing app typography.
