package app.netguard.engine.dns

import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * FakeIpManager — manages the Fake-IP pool for domain-based routing.
 *
 * Fake-IP mode allows domain-based routing decisions even for apps that
 * perform DNS resolution before connecting. Without Fake-IP, we would
 * only see the resolved IP address and lose the domain information.
 *
 * How it works:
 * 1. App queries DNS for "mail.google.com"
 * 2. Our DNS engine returns a fake IP: 198.18.1.42 (from pool)
 * 3. Bidirectional mapping is stored: 198.18.1.42 ↔ "mail.google.com"
 * 4. App connects to 198.18.1.42
 * 5. Rule engine looks up 198.18.1.42 → "mail.google.com" → apply domain rules
 * 6. Proxy connects to actual "mail.google.com" (domain-based, no local resolution)
 *
 * Pool: 198.18.0.0/15 (RFC 2544 — benchmarking, never routed on internet)
 *   Total addresses: 131,072
 *   Practical limit: ~50,000 concurrent domains (with TTL eviction)
 *
 * LRU eviction: when pool full, oldest mapping is evicted.
 *
 * Thread safety: fully thread-safe via ConcurrentHashMap + AtomicInteger.
 */
class FakeIpManager(
    private val baseAddress: Int = FAKE_IP_BASE,
    private val poolSize: Int = DEFAULT_POOL_SIZE,
) {
    // ip → domain
    private val ipToDomain = ConcurrentHashMap<Int, String>(poolSize)
    // domain → ip
    private val domainToIp = ConcurrentHashMap<String, Int>(poolSize)
    // LRU tracking (simplified — in production use LinkedHashMap with accessOrder)
    private val allocationOrder = ArrayDeque<Int>(poolSize)
    private val counter = AtomicInteger(0)
    private val lock = Any()

    /**
     * Assign a fake IP for [domain], or return the existing one.
     *
     * @return InetAddress from the fake-IP pool
     */
    fun getOrAssign(domain: String): InetAddress {
        val normalizedDomain = domain.lowercase().trimEnd('.')

        // Fast path: already assigned
        domainToIp[normalizedDomain]?.let { ip ->
            return intToAddress(ip)
        }

        // Slow path: assign new IP
        synchronized(lock) {
            // Double-check after acquiring lock
            domainToIp[normalizedDomain]?.let { return intToAddress(it) }

            // Evict if pool is full
            if (ipToDomain.size >= poolSize) {
                evictOldest()
            }

            // Assign next IP from pool (wraps around)
            val offset = counter.getAndIncrement() % poolSize
            val ip = baseAddress + offset

            ipToDomain[ip] = normalizedDomain
            domainToIp[normalizedDomain] = ip
            allocationOrder.addLast(ip)

            return intToAddress(ip)
        }
    }

    /**
     * Look up the original domain for a fake IP.
     * Returns null if [ip] is not a fake IP or mapping has been evicted.
     */
    fun lookupDomain(ip: InetAddress): String? {
        if (!isFakeIp(ip)) return null
        val intIp = addressToInt(ip)
        return ipToDomain[intIp]
    }

    /**
     * Check if an IP address is in the fake-IP pool.
     */
    fun isFakeIp(ip: InetAddress): Boolean {
        if (ip !is Inet4Address) return false
        val intIp = addressToInt(ip)
        return intIp in baseAddress until (baseAddress + poolSize)
    }

    /**
     * Remove a mapping (called when TTL expires).
     */
    fun evict(domain: String) {
        synchronized(lock) {
            val ip = domainToIp.remove(domain) ?: return
            ipToDomain.remove(ip)
            allocationOrder.remove(ip)
        }
    }

    /** Current number of active mappings */
    val size: Int get() = ipToDomain.size

    private fun evictOldest() {
        val oldest = allocationOrder.removeFirstOrNull() ?: return
        val domain = ipToDomain.remove(oldest)
        if (domain != null) domainToIp.remove(domain)
    }

    private fun intToAddress(ip: Int): InetAddress {
        val bytes = byteArrayOf(
            (ip shr 24 and 0xFF).toByte(),
            (ip shr 16 and 0xFF).toByte(),
            (ip shr 8 and 0xFF).toByte(),
            (ip and 0xFF).toByte(),
        )
        return InetAddress.getByAddress(bytes)
    }

    private fun addressToInt(ip: InetAddress): Int {
        val bytes = ip.address
        return (bytes[0].toInt() and 0xFF shl 24) or
               (bytes[1].toInt() and 0xFF shl 16) or
               (bytes[2].toInt() and 0xFF shl 8) or
               (bytes[3].toInt() and 0xFF)
    }

    companion object {
        // 198.18.0.0 as integer
        val FAKE_IP_BASE: Int = (198 shl 24) or (18 shl 16)
        const val DEFAULT_POOL_SIZE = 65536
    }
}
