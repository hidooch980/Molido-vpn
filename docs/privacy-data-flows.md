# Privacy data flows — current implementation

## VPN egress verification and approximate location

After the WireGuard backend reports that its tunnel is active, MolidoVPN makes an HTTPS request to `https://ipwho.is/` from the Android app process. The request is intended to travel through the active VPN tunnel.

The provider observes the public IP address of the VPN exit endpoint and returns:
- the observed public egress IP;
- approximate country, region and city associated with that IP.

This is IP-based geolocation, not GPS. City/region estimates can be imprecise, especially for mobile networks, carrier NAT, hosting networks, and anycast infrastructure.

## Data sent to the provider

The provider necessarily receives the network source IP of the HTTPS request (expected to be the VPN egress IP). MolidoVPN does not send the WireGuard private key, tunnel profile, browsing history, DNS query history, or device GPS coordinates to this provider.

The free endpoint currently advertises 1,000 requests per day and permits commercial use, but does not provide an uptime SLA. Recheck the provider terms before each production release and consider a paid plan or a self-hosted geolocation database if the service becomes business-critical.

Provider documentation and terms:
- https://ipwhois.io/documentation
- https://ipwhois.io/pricing

## User-facing disclosure

The app must make it clear that public IP and approximate location are looked up by a third-party service. Do not label this as precise location or imply that MolidoVPN obtains GPS.

## Retention and failure behavior

- Do not persist the egress IP or geolocation unless the user explicitly enables a history feature.
- Clear current egress details when the tunnel is disconnected or verification fails.
- If the provider is unavailable or rate-limited, show location as unavailable and do not report the egress check as passed.
- Never substitute a configured server's country or a fixed sample IP for live results.

This document describes the current code path; it is not a substitute for a complete legal privacy policy or jurisdiction-specific review.
