# ADR-001: VPN Approach — VpnService + TUN

**Date:** 2026-06-26  
**Status:** Accepted

## Context
Android provides multiple ways to intercept network traffic:
1. VpnService + TUN interface (userspace)
2. iptables + tproxy (requires root)
3. Accessibility Service (deprecated, unstable)
4. Network callbacks (read-only, cannot redirect)

## Decision
Use **Android VpnService API** with a TUN virtual interface.

## Rationale
- No root required — mainstream market accessible
- Official Android API — stable across versions
- Supports per-app bypass via `VpnService.Builder.addAllowedApplication()`
- TUN provides full L3 visibility (IP packets)

## Consequences
- Userspace packet processing adds ~0.5-2ms latency overhead
- Cannot use kernel-space optimizations (iptables NAT, TPROXY)
- Must handle TCP reassembly and UDP tracking in userspace
