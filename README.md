<p align="center">
  <img src="assets/branding/ic_netguard_shield.svg" width="120" alt="NetGuard Pro Shield"/>
</p>

<h1 align="center">NetGuard Pro</h1>

<p align="center">
  <strong>Next-generation Android traffic routing platform</strong><br/>
  Per-app proxy routing · Rule engine · DoH/DoT DNS · No root required
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-green?logo=android" alt="Android 8+"/>
  <img src="https://img.shields.io/badge/Kotlin-2.1-blue?logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License"/>
  <img src="https://img.shields.io/github/actions/workflow/status/xxx5rive2-ops/proxyvpn-android/ci.yml?label=CI" alt="CI"/>
</p>

---

## ✨ Features

| Feature | Description |
|---|---|
| **Per-app routing** | Route specific apps through proxy, others direct |
| **Compiled Rule Engine** | Trie-based O(log n) rule matching — scales to 100K+ rules |
| **Multi-protocol** | SOCKS5, HTTP(S), Shadowsocks, VMess, Trojan |
| **Fake-IP DNS** | Domain-based routing even for IP connections |
| **DoH / DoT / DoQ** | Encrypted DNS, zero DNS leaks |
| **IPv4 + IPv6** | Full dual-stack support |
| **Load balancing** | Round-robin, least-latency, weighted, failover |
| **Circuit breaker** | Auto-failover when proxy is unreachable |
| **Kill switch** | Block traffic when VPN disconnects |
| **No root** | Uses Android VpnService API |
| **Hardware security** | Credentials in Android Keystore (hardware-backed) |
| **Quick Settings tile** | Toggle from notification shade |

---

## 📦 Download

### Latest Release

| Architecture | File | Target devices |
|---|---|---|
| **Universal** | `netguard-pro-universal.apk` | All Android devices |
| arm64-v8a | `netguard-pro-arm64.apk` | Modern phones (2016+) |
| armeabi-v7a | `netguard-pro-arm32.apk` | Older phones |
| x86_64 | `netguard-pro-x86_64.apk` | Emulators, some Chromebooks |
| **AAB** | `netguard-pro.aab` | Play Store submission |

→ [Download from Releases](https://github.com/xxx5rive2-ops/proxyvpn-android/releases)

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────┐
│              PRESENTATION (Jetpack Compose)          │
│     Dashboard · Rules · Servers · Traffic · Settings│
└──────────────────────┬──────────────────────────────┘
                       │ MVVM + Hilt
┌──────────────────────▼──────────────────────────────┐
│              DOMAIN (Pure Kotlin)                    │
│        Entities · UseCases · Repository Ports        │
└──────────────────────┬──────────────────────────────┘
                       │ Repository implementations
┌──────────────────────▼──────────────────────────────┐
│         DATA / ENGINE (Android + Kotlin)             │
│  VPN/TUN · Rule Compiler · SOCKS5 · DNS · Room DB   │
└─────────────────────────────────────────────────────┘
```

**Process isolation:** The VPN engine runs in a separate Android process (`:vpn_engine`), providing crash isolation and security boundaries.

---

## 🚀 Build

### Prerequisites
- Android Studio Ladybug (2024.2+) or newer
- JDK 17+
- Android SDK API 35

### Debug build
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/
```

### Release build (requires keystore)
```bash
# Create keystore.properties (see docs/signing.md)
./gradlew assembleRelease
./gradlew bundleRelease

# Outputs:
# app/build/outputs/apk/release/netguard-pro-universal-release.apk  ← all devices
# app/build/outputs/apk/release/netguard-pro-arm64-v8a-release.apk  ← 64-bit ARM
# app/build/outputs/apk/release/netguard-pro-armeabi-v7a-release.apk
# app/build/outputs/apk/release/netguard-pro-x86_64-release.apk
# app/build/outputs/bundle/release/netguard-pro-release.aab          ← Play Store
```

### Run tests
```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew lint                # lint
```

---

## 📐 Module Structure

```
app/                    ← Application shell (entry point only)
core/
  core-common/          ← Shared utilities, extensions
  core-domain/          ← Entities, UseCases, Repository interfaces (pure Kotlin)
  core-data/            ← Repository implementations
  core-database/        ← Room DB, DAOs, migrations
  core-network/         ← OkHttp, Retrofit, network utilities
  core-security/        ← Keystore, AES-256-GCM, biometric
  core-ui/              ← Design system, Compose components
engine/
  engine-vpn/           ← VpnService, TUN interface, packet pipeline
  engine-proxy/         ← SOCKS5, HTTP, Shadowsocks connectors
  engine-rules/         ← Rule compiler, DomainTrie, IpCidrTrie
  engine-dns/           ← DoH/DoT resolver, Fake-IP manager
  engine-traffic/       ← Traffic analysis, bandwidth metering
features/
  feature-dashboard/    ← Connection status, real-time stats
  feature-rules/        ← Rule management
  feature-servers/      ← Proxy server CRUD
  feature-traffic/      ← Traffic log viewer
  feature-settings/     ← App settings
  feature-diagnostics/  ← Debug tools
```

---

## 🔒 Security

- Credentials stored in **Android Keystore** (hardware-backed AES-256-GCM)
- **Zero DNS leaks** — all DNS through our encrypted engine
- **TLS 1.3** minimum for proxy connections
- **Certificate pinning** for internal API
- **No telemetry** by default — privacy first
- OWASP Mobile Top 10 compliant

→ See [SECURITY.md](SECURITY.md) for vulnerability disclosure policy.

---

## 📄 License

MIT License — see [LICENSE](LICENSE)

---

## 🤝 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)
