# Contributing to NetGuard Pro

## Development Workflow

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit with conventional commits: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`
4. Ensure all tests pass: `./gradlew testDebugUnitTest`
5. Ensure lint passes: `./gradlew lint`
6. Open a Pull Request against `develop`

## Code Standards

- **Kotlin** — idiomatic Kotlin, no Java unless required
- **Coroutines** — no RxJava, no callbacks where coroutines suffice  
- **Testing** — JUnit5 + Kotest + MockK; new code must have unit tests
- **Architecture** — Clean Architecture layers must be respected (no domain → Android deps)
- **Security** — never log sensitive data; never store credentials in plaintext

## Commit Convention

```
feat(engine): add UDP Associate support for SOCKS5
fix(rules): correct wildcard matching for nested subdomains
docs(arch): update VPN process isolation diagram
test(dns): add FakeIpManager pool eviction tests
refactor(proxy): extract ConnectionPool to separate class
perf(rules): replace regex matching with compiled trie
security(creds): zero credential bytes after use
```

## Pull Request Checklist

- [ ] Unit tests added/updated
- [ ] No new lint warnings
- [ ] No hardcoded credentials or secrets
- [ ] Architecture layers respected
- [ ] Thread safety considered and documented
- [ ] Memory implications noted (large allocations, leaks)
