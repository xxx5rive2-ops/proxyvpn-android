package app.netguard.engine.dns

import java.net.Inet4Address
import java.net.InetAddress
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * FakeIpManager — manages the Fake-IP pool for domain-based routing.
 *
 * Assigns synthetic IPs from 198.18.0.0/15 (RFC 2544 benchmark range)
 * to domain names, enabling domain-based routing for IP connections.
 *
 * Thread safety: synchronized on [lock] for mutation operations.
 */
class FakeIpManager(
    private val baseAddress: Int = FAKE_IP_BASE,
    private val poolSize: Int = DEFAULT_POOL_SIZE,
) {
    private val ipToDomain  = ConcurrentHashMap<Int, String>(poolSize)
    private val domainToIp  = ConcurrentHashMap<String, Int>(poolSize)
    // LinkedList avoids remove(index) ambiguity — use removeFirstOccurrence(element)
    private val allocOrder  = LinkedList<Int>()
    private val counter     = AtomicInteger(0)
    private val lock        = Any()

    /** Assign a fake IP for [domain], or return the existing assignment. */
    fun getOrAssign(domain: String): InetAddress {
        val normalized = domain.lowercase().trimEnd('.')
        domainToIp[normalized]?.let { return intToAddress(it) }
        synchronized(lock) {
            domainToIp[normalized]?.let { return intToAddress(it) }
            if (ipToDomain.size >= poolSize) evictOldest()
            val ip = baseAddress + (counter.getAndIncrement() % poolSize)
            ipToDomain[ip] = normalized
            domainToIp[normalized] = ip
            allocOrder.addLast(ip)
            return intToAddress(ip)
        }
    }

    /** Look up the original domain for a Fake-IP address. Returns null if not a fake IP. */
    fun lookupDomain(ip: InetAddress): String? {
        if (!isFakeIp(ip)) return null
        return ipToDomain[addressToInt(ip)]
    }

    /** Returns true if [ip] is in the Fake-IP pool range. */
    fun isFakeIp(ip: InetAddress): Boolean {
        if (ip !is Inet4Address) return false
        val intIp = addressToInt(ip)
        return intIp in baseAddress until (baseAddress + poolSize)
    }

    /** Remove a mapping (e.g. when TTL expires). */
    fun evict(domain: String) {
        synchronized(lock) {
            val ip = domainToIp.remove(domain) ?: return
            ipToDomain.remove(ip)
            allocOrder.removeFirstOccurrence(ip)  // LinkedList — no ambiguity
        }
    }

    /** Current number of active mappings. */
    val size: Int get() = ipToDomain.size

    private fun evictOldest() {
        val oldest = allocOrder.pollFirst() ?: return
        val domain = ipToDomain.remove(oldest)
        if (domain != null) domainToIp.remove(domain)
    }

    private fun intToAddress(ip: Int): InetAddress = InetAddress.getByAddress(
        byteArrayOf(
            (ip shr 24 and 0xFF).toByte(),
            (ip shr 16 and 0xFF).toByte(),
            (ip shr 8  and 0xFF).toByte(),
            (ip        and 0xFF).toByte(),
        )
    )

    private fun addressToInt(ip: InetAddress): Int {
        val b = ip.address
        return (b[0].toInt() and 0xFF shl 24) or
               (b[1].toInt() and 0xFF shl 16) or
               (b[2].toInt() and 0xFF shl 8)  or
               (b[3].toInt() and 0xFF)
    }

    companion object {
        val FAKE_IP_BASE: Int = (198 shl 24) or (18 shl 16)
        const val DEFAULT_POOL_SIZE = 65536
    }
}
