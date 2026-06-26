package app.netguard.engine.rules

import app.netguard.domain.entity.RuleAction

/**
 * IpCidrTrie — binary radix trie for IP/CIDR matching.
 *
 * Implements a Patricia Trie for efficient IP address and CIDR range matching.
 * Both IPv4 (/0-/32) and IPv6 (/0-/128) are supported.
 *
 * Performance:
 * - Lookup: O(32) for IPv4, O(128) for IPv6 — constant time
 * - Insert: O(32) or O(128)
 *
 * Thread safety: IMMUTABLE after build — safe for concurrent reads.
 */
class IpCidrTrie private constructor(
    private val ipv4Root: TrieNode?,
    private val ipv6Root: TrieNode?,
) {

    fun lookup(ipString: String): RuleAction? {
        return if (ipString.contains(':')) {
            val bytes = parseIPv6(ipString) ?: return null
            lookupBits(ipv6Root, bytes, 0, 128)
        } else {
            val addr = parseIPv4(ipString) ?: return null
            lookupBits(ipv4Root, longToBytes(addr), 0, 32)
        }
    }

    private fun lookupBits(node: TrieNode?, bytes: ByteArray, bitIndex: Int, maxBits: Int): RuleAction? {
        if (node == null) return null
        if (bitIndex == maxBits) return node.action
        val bit = getBit(bytes, bitIndex)
        val deeper = if (bit == 0) lookupBits(node.left, bytes, bitIndex + 1, maxBits)
                     else lookupBits(node.right, bytes, bitIndex + 1, maxBits)
        return deeper ?: node.action
    }

    private fun getBit(bytes: ByteArray, bitIndex: Int): Int {
        val byteIdx = bitIndex / 8
        val bitIdx = 7 - (bitIndex % 8)
        if (byteIdx >= bytes.size) return 0
        return (bytes[byteIdx].toInt() shr bitIdx) and 1
    }

    private fun parseIPv4(ip: String): Long? = try {
        val parts = ip.trim().split(".")
        if (parts.size != 4) null
        else parts.fold(0L) { acc, part ->
            val octet = part.toLong()
            if (octet !in 0..255) return null
            (acc shl 8) or octet
        }
    } catch (e: NumberFormatException) { null }

    private fun longToBytes(value: Long): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8)  and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun parseIPv6(ip: String): ByteArray? = try {
        java.net.Inet6Address.getByName(ip).address
    } catch (e: Exception) { null }

    data class TrieNode(
        val action: RuleAction?,
        val left: TrieNode?,   // bit = 0
        val right: TrieNode?,  // bit = 1
    )

    /** Mutable entry during build phase */
    private data class CidrEntry(
        val bytes: ByteArray,
        val prefixLen: Int,
        val action: RuleAction,
    )

    class Builder {
        private val ipv4Entries = mutableListOf<CidrEntry>()
        private val ipv6Entries = mutableListOf<CidrEntry>()

        fun addCidr(cidr: String, action: RuleAction): Builder {
            val parts = cidr.trim().split("/")
            if (parts.size != 2) return this
            val prefixLen = parts[1].toIntOrNull() ?: return this
            if (parts[0].contains(':')) {
                val bytes = try { java.net.Inet6Address.getByName(parts[0]).address }
                            catch (e: Exception) { return this }
                ipv6Entries.add(CidrEntry(bytes, prefixLen.coerceIn(0, 128), action))
            } else {
                val addr = parseIPv4Static(parts[0]) ?: return this
                ipv4Entries.add(CidrEntry(longToBytesStatic(addr), prefixLen.coerceIn(0, 32), action))
            }
            return this
        }

        fun addIp(ip: String, action: RuleAction): Builder =
            if (ip.contains(':')) addCidr("$ip/128", action)
            else addCidr("$ip/32", action)

        fun build(): IpCidrTrie {
            val ipv4Root = if (ipv4Entries.isEmpty()) null
                           else buildNode(ipv4Entries, 0, 32)
            val ipv6Root = if (ipv6Entries.isEmpty()) null
                           else buildNode(ipv6Entries, 0, 128)
            return IpCidrTrie(ipv4Root, ipv6Root)
        }

        private fun buildNode(entries: List<CidrEntry>, bitIndex: Int, maxBits: Int): TrieNode? {
            if (entries.isEmpty()) return null
            val terminatedHere = entries.filter { it.prefixLen == bitIndex }
            val action = terminatedHere.lastOrNull()?.action
            if (bitIndex == maxBits) return TrieNode(action, null, null)
            val continuing = entries.filter { it.prefixLen > bitIndex }
            val left  = buildNode(continuing.filter { getBitStatic(it.bytes, bitIndex) == 0 }, bitIndex + 1, maxBits)
            val right = buildNode(continuing.filter { getBitStatic(it.bytes, bitIndex) == 1 }, bitIndex + 1, maxBits)
            return TrieNode(action, left, right)
        }

        private fun getBitStatic(bytes: ByteArray, bitIndex: Int): Int {
            val byteIdx = bitIndex / 8
            val bitIdx  = 7 - (bitIndex % 8)
            if (byteIdx >= bytes.size) return 0
            return (bytes[byteIdx].toInt() shr bitIdx) and 1
        }

        private fun parseIPv4Static(ip: String): Long? = try {
            val parts = ip.trim().split(".")
            if (parts.size != 4) null
            else parts.fold(0L) { acc, part ->
                val octet = part.toLong()
                if (octet !in 0..255) return null
                (acc shl 8) or octet
            }
        } catch (e: NumberFormatException) { null }

        private fun longToBytesStatic(v: Long): ByteArray = byteArrayOf(
            ((v shr 24) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 8)  and 0xFF).toByte(),
            (v and 0xFF).toByte(),
        )
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
