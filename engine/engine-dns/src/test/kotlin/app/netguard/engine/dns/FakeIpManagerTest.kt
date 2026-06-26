package app.netguard.engine.dns

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.Inet4Address

class FakeIpManagerTest {

    private lateinit var manager: FakeIpManager

    @BeforeEach
    fun setup() {
        manager = FakeIpManager(poolSize = 100)
    }

    @Test
    fun `assigns unique IPs to different domains`() {
        val ip1 = manager.getOrAssign("google.com")
        val ip2 = manager.getOrAssign("facebook.com")
        ip1 shouldNotBe ip2
    }

    @Test
    fun `same domain always gets same IP`() {
        val ip1 = manager.getOrAssign("google.com")
        val ip2 = manager.getOrAssign("google.com")
        ip1 shouldBe ip2
    }

    @Test
    fun `lookupDomain returns original domain`() {
        val ip = manager.getOrAssign("mail.google.com")
        manager.lookupDomain(ip) shouldBe "mail.google.com"
    }

    @Test
    fun `isFakeIp returns true for pool IPs`() {
        val ip = manager.getOrAssign("example.com")
        manager.isFakeIp(ip) shouldBe true
    }

    @Test
    fun `isFakeIp returns false for real IPs`() {
        val realIp = java.net.InetAddress.getByName("8.8.8.8")
        manager.isFakeIp(realIp) shouldBe false
    }

    @Test
    fun `domain normalization is case insensitive`() {
        val ip1 = manager.getOrAssign("GOOGLE.COM")
        val ip2 = manager.getOrAssign("google.com")
        ip1 shouldBe ip2
    }

    @Test
    fun `evict removes mapping`() {
        manager.getOrAssign("evict-me.com")
        manager.evict("evict-me.com")
        // After eviction, same domain gets potentially different IP
        // but lookup returns null for old IP (pool reused)
        manager.size shouldBe 0
    }

    @Test
    fun `assigned IPs are in fake-IP range`() {
        val ip = manager.getOrAssign("test.com") as Inet4Address
        val bytes = ip.address
        // 198.18.x.x range
        bytes[0].toInt() and 0xFF shouldBe 198
        bytes[1].toInt() and 0xFF shouldBe 18
    }

    @Test
    fun `pool evicts oldest when full`() {
        val smallManager = FakeIpManager(poolSize = 3)
        smallManager.getOrAssign("a.com")
        smallManager.getOrAssign("b.com")
        smallManager.getOrAssign("c.com")
        smallManager.size shouldBe 3
        // This triggers eviction of oldest (a.com)
        smallManager.getOrAssign("d.com")
        smallManager.size shouldBe 3
    }
}
