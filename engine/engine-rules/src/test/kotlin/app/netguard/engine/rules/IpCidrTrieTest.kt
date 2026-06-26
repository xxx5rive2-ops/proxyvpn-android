package app.netguard.engine.rules

import app.netguard.domain.entity.RuleAction
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IpCidrTrieTest {

    @ParameterizedTest(name = "lookup({0}) should be {1}")
    @CsvSource(
        "192.168.1.1,    LAN",        // matches 192.168.0.0/16
        "192.168.255.254,LAN",        // edge of range
        "10.0.0.1,       LAN10",      // matches 10.0.0.0/8
        "10.255.255.255, LAN10",
        "8.8.8.8,        DNS",        // exact IP
        "8.8.4.4,        NO_MATCH",   // not in any rule
        "172.16.0.1,     NO_MATCH",
        "0.0.0.0,        NO_MATCH",
    )
    fun `IP CIDR lookup returns correct action`(ip: String, expected: String) {
        val trie = IpCidrTrie.builder()
            .addCidr("192.168.0.0/16", RuleAction.Direct)
            .addCidr("10.0.0.0/8", RuleAction.Proxy("lan10"))
            .addIp("8.8.8.8", RuleAction.Proxy("dns"))
            .build()

        val result = trie.lookup(ip)
        when (expected) {
            "NO_MATCH" -> result shouldBe null
            "LAN"      -> result shouldBe RuleAction.Direct
            "LAN10"    -> result shouldBe RuleAction.Proxy("lan10")
            "DNS"      -> result shouldBe RuleAction.Proxy("dns")
        }
    }

    @Test
    fun `more specific CIDR wins over less specific`() {
        val trie = IpCidrTrie.builder()
            .addCidr("10.0.0.0/8", RuleAction.Direct)       // broad
            .addCidr("10.10.0.0/16", RuleAction.Block)      // specific
            .addCidr("10.10.10.0/24", RuleAction.Proxy("s")) // most specific
            .build()

        trie.lookup("10.10.10.5") shouldBe RuleAction.Proxy("s")
        trie.lookup("10.10.20.5") shouldBe RuleAction.Block
        trie.lookup("10.20.0.1")  shouldBe RuleAction.Direct
        trie.lookup("11.0.0.1")   shouldBe null
    }

    @Test
    fun `empty trie returns null`() {
        val trie = IpCidrTrie.builder().build()
        trie.lookup("1.2.3.4") shouldBe null
    }

    @Test
    fun `handles IPv6 addresses`() {
        val trie = IpCidrTrie.builder()
            .addCidr("2001:db8::/32", RuleAction.Block)
            .build()
        // IPv6 matching — basic test
        trie.lookup("2001:db8::1") shouldBe RuleAction.Block
        trie.lookup("2001:db9::1") shouldBe null
    }
}
