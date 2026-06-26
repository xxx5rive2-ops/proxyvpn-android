package app.netguard.engine.rules

import app.netguard.domain.entity.RuleAction
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DomainTrieTest {

    private lateinit var trie: DomainTrie

    @BeforeEach
    fun setup() {
        trie = DomainTrie.builder()
            .addExact("example.com", RuleAction.Direct)
            .addSuffix("google.com", RuleAction.Proxy("server-1"))
            .addWildcard("*.ads.com", RuleAction.Block)
            .addExact("mail.google.com", RuleAction.Direct) // overrides suffix
            .build()
    }

    @ParameterizedTest(name = "lookup({0}) should be {1}")
    @CsvSource(
        "example.com,          DIRECT",
        "sub.example.com,      NO_MATCH",   // exact only — no suffix match
        "google.com,           PROXY",
        "mail.google.com,      DIRECT",      // exact overrides suffix
        "docs.google.com,      PROXY",       // suffix match
        "a.b.google.com,       PROXY",       // deep suffix match
        "notgoogle.com,        NO_MATCH",
        "tracker.ads.com,      BLOCK",       // wildcard match
        "ads.com,              NO_MATCH",    // wildcard requires subdomain
        "GOOGLE.COM,           PROXY",       // case insensitive
    )
    fun `domain lookup returns correct action`(domain: String, expected: String) {
        val result = trie.lookup(domain)
        withClue("lookup($domain)") {
            when (expected) {
                "NO_MATCH" -> result shouldBe null
                "DIRECT"   -> result shouldBe RuleAction.Direct
                "PROXY"    -> result shouldBe RuleAction.Proxy("server-1")
                "BLOCK"    -> result shouldBe RuleAction.Block
            }
        }
    }

    @Test
    fun `empty trie returns null for all lookups`() {
        val empty = DomainTrie.builder().build()
        empty.lookup("google.com") shouldBe null
        empty.lookup("example.com") shouldBe null
    }

    @Test
    fun `exact match takes priority over wildcard for same domain`() {
        val result = trie.lookup("mail.google.com")
        result shouldBe RuleAction.Direct // exact, not suffix PROXY
    }

    @Test
    fun `trie handles single-label domains`() {
        val t = DomainTrie.builder()
            .addExact("localhost", RuleAction.Direct)
            .build()
        t.lookup("localhost") shouldBe RuleAction.Direct
        t.lookup("notlocalhost") shouldBe null
    }

    @Test
    fun `trie handles deeply nested subdomains`() {
        val t = DomainTrie.builder()
            .addSuffix("internal.corp", RuleAction.Direct)
            .build()
        t.lookup("a.b.c.d.internal.corp") shouldBe RuleAction.Direct
        t.lookup("internal.corp") shouldBe RuleAction.Direct
        t.lookup("corp") shouldBe null
    }
}
