# Changelog

All notable changes to NetGuard Pro will be documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)
Versioning: [Semantic Versioning](https://semver.org/)

## [Unreleased]

### Added
- Initial project structure and architecture
- Multi-module Android project (18 modules)
- VPN/TUN packet processing pipeline
- SOCKS5 connector (RFC 1928 + RFC 1929)
- Compiled rule engine with DomainTrie and IpCidrTrie
- Fake-IP DNS manager with LRU pool eviction
- Room database with type-safe DAOs
- Android Keystore AES-256-GCM credential vault
- Jetpack Compose UI with Material 3
- Universal APK + per-ABI split builds
- GitHub Actions CI/CD pipeline
- NetGuard Pro brand identity and icon

## [1.0.0] - Planned

### Features (Planned)
- Per-app proxy routing
- SOCKS5, HTTP, Shadowsocks support
- Rule engine with 10+ condition types
- DoH/DoT/DoQ DNS
- Fake-IP mode
- Traffic monitoring dashboard
- Load balancing + circuit breaker
- Kill switch
- Quick Settings tile
