# NetGuard Pro — Architecture Overview

## System Design

NetGuard Pro follows Clean Architecture with MVVM presentation pattern,
organized as a multi-module Android project.

### Layer Boundaries

```
Presentation ──► Domain ◄── Data/Infrastructure
     ↕               ↕
  ViewModels      UseCases
  Composables     Entities
  Navigation      Repository Interfaces
```

**Dependency Rule:** Outer layers depend on inner layers. Domain never imports Android.

### Process Architecture

Two Android processes:
1. **UI Process** (`:app`) — Compose UI, ViewModels, navigation
2. **VPN Engine Process** (`:vpn_engine`) — TUN interface, packet processing

Communication via Android Binder IPC.
If VPN engine crashes, UI process survives and can offer reconnection.

### Module Dependency Graph

```
app
 ├── core-common
 ├── core-domain        (pure Kotlin — no Android)
 ├── core-data          → core-domain, core-database
 ├── core-database      → core-domain
 ├── core-network       → core-common
 ├── core-security      → core-common
 ├── core-ui            (Compose design system)
 ├── engine-vpn         → engine-rules, engine-proxy, engine-dns
 ├── engine-proxy       → core-security
 ├── engine-rules       → core-domain
 ├── engine-dns         → core-common
 ├── engine-traffic     → core-domain
 └── feature-*          → core-domain, core-ui, Hilt
```

## Key Design Decisions

See [adr/](adr/) for Architecture Decision Records.
