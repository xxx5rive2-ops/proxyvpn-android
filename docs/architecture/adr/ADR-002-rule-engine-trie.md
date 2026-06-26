# ADR-002: Rule Engine — Compiled Trie vs Runtime Regex

**Date:** 2026-06-26  
**Status:** Accepted

## Context
Rule matching happens on every packet — must be extremely fast.

Options:
1. Sequential list + regex — O(n × m) per packet
2. HashMap — O(1) exact, no wildcard support
3. Compiled Trie — O(d) domain, O(32) IP

## Decision
**Compiled DomainTrie + IpCidrTrie** built at startup, immutable at runtime.

## Rationale
With 10,000 rules and 1ms regex per rule: 10 seconds per packet = unusable.
Trie gives O(3-5) for typical 3-label domains, regardless of rule count.

## Consequences
- More complex implementation
- Compilation required when rules change (100-500ms, done off main thread)
- Hot-reload via AtomicReference swap (zero downtime)
