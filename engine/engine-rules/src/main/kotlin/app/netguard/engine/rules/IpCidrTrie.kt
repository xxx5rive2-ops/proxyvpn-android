package app.netguard.engine.rules

import app.netguard.domain.entity.RuleAction

/**
 * IpCidrTrie — binary radix trie for IP/CIDR matching.
 *
 * Implements a Patricia Trie (compressed binary trie) for efficient
 * IP address and CIDR range matching.
 *
 * Algorithm:
 * - IP addresses are treated as 32-bit (IPv4) or 128-bit (IPv6) integers
 * - Each bit of the address determines left (0) or right (1) traversal
 * - CIDR /n means we only care about the first n bits
 *
 * Performance:
 * - Lookup: O(32) for IPv4, O(128) for IPv6 — constant time!
 * - Insert: O(32) or O(128)
 * - Memory: O(n × 32) where n = number of CIDR rules
 *
 * This implementation handles both IPv4 and IPv6 in separate tries.
 */
class IpCidrTrie private constructor(
    private val ipv4Root: TrieNode?,
    private val ipv6Root: TrieNode?,
) {

    /**
     * Look up an IP address and return the most specific matching action.
     *
     * @param ipString IPv4 or IPv6 address string
     * @return Matching action or null
     */
    fun lookup(ipString: String): RuleAction? {
        return if (ipString.contains(':')) {
            // IPv6
            val bytes = parseIPv6(ipString) ?: return null
            lookupBits(ipv6Root, bytes, 0, 128)
        } else {
            // IPv4
            val addr = parseIPv4(ipString) ?: return null
            lookupBits(ipv4Root, longToBytes(addr), 0, 32)
        }
    }

    private fun lookupBits(node: TrieNode?, bytes: ByteArray, bitIndex: Int, maxBits: Int): RuleAction? {
        if (node == null) return null
        if (bitIndex == maxBits) return node.action

        val bit = getBit(bytes, bitIndex)
        val deeper = if (bit == 0) {
            lookupBits(node.left, bytes, bitIndex + 1, maxBits)
        } else {
            lookupBits(node.right, bytes, bitIndex + 1, maxBits)
        }

        // More specific match wins; fall back to this node's action
        return deeper ?: node.action
    }

    private fun getBit(bytes: ByteArray, bitIndex: Int): Int {
        val byteIdx = bitIndex / 8
        val bitIdx = 7 - (bitIndex % 8)
        if (byteIdx >= bytes.size) return 0
        return (bytes[byteIdx].toInt() shr bitIdx) and 1
    }

    private fun parseIPv4(ip: String): Long? {
        return try {
            val parts = ip.trim().split(".")
            if (parts.size != 4) return null
            parts.fold(0L) { acc, part ->
                val octet = part.toLong()
                if (octet < 0 || octet > 255) return null
                (acc shl 8) or octet
            }
        } catch (e: NumberFormatException) { null }
    }

    private fun longToBytes(value: Long): ByteArray {
        return byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte(),
        )
    }

    private fun parseIPv6(ip: String): ByteArray? {
        return try {
            java.net.Inet6Address.getByName(ip).address
        } catch (e: Exception) { null }
    }

    data class TrieNode(
        val action: RuleAction?,
        val left: TrieNode?,  // bit = 0
        val right: TrieNode?, // bit = 1
    )

    class Builder {
        private val ipv4Entries = mutableListOf<Pair<Long, Int, RuleAction>>()
        private val ipv6Entries = mutableListOf<Pair<ByteArray, Int, RuleAction>>()

        // Kotlin doesn't support Triple destructuring in data class, use list of Triple
        private val ipv4List = mutableListOf<Triple<Long, Int, RuleAction>>()
        private val ipv6List = mutableListOf<Triple<ByteArray, Int, RuleAction>>()

        fun addCidr(cidr: String, action: RuleAction): Builder {
            val parts = cidr.trim().split("/")
            if (parts.size != 2) return this
            val prefixLen = parts[1].toIntOrNull() ?: return this

            if (parts[0].contains(':')) {
                // IPv6
                val bytes = try {
                    java.net.Inet6Address.getByName(parts[0]).address
                } catch (e: Exception) { return this }
                ipv6List.add(Triple(bytes, prefixLen.coerceIn(0, 128), action))
            } else {
                // IPv4
                val addr = parseIPv4String(parts[0]) ?: return this
                ipv4List.add(Triple(addr, prefixLen.coerceIn(0, 32), action))
            }
            return this
        }

        fun addIp(ip: String, action: RuleAction): Builder {
            return if (ip.contains(':')) addCidr("$ip/128", action)
            else addCidr("$ip/32", action)
        }

        fun build(): IpCidrTrie {
            val ipv4Root = if (ipv4List.isEmpty()) null else {
                buildTrieNode(
                    entries = ipv4List.map { (addr, prefix, act) ->
                        Pair(longToBytes(addr), Pair(prefix, act))
                    },
                    bitIndex = 0,
                    maxBits = 32
                )
            }
            val ipv6Root = if (ipv6List.isEmpty()) null else {
                buildTrieNode(
                    entries = ipv6List.map { (bytes, prefix, act) ->
                        Pair(bytes, Pair(prefix, act))
                    },
                    bitIndex = 0,
                    maxBits = 128
                )
            }
            return IpCidrTrie(ipv4Root, ipv6Root)
        }

        private fun buildTrieNode(
            entries: List<Pair<ByteArray, Pair<Int, RuleAction>>>,
            bitIndex: Int,
            maxBits: Int,
        ): TrieNode? {
            if (entries.isEmpty()) return null

            val terminatedHere = entries.filter { (_, prefixAct) -> prefixAct.first == bitIndex }
            val action = terminatedHere.lastOrNull()?.second?.second

            if (bitIndex == maxBits) return TrieNode(action, null, null)

            val leftEntries = entries
                .filter { (bytes, prefixAct) -> prefixAct.first > bitIndex && getBit(bytes, bitIndex) == 0 }
            val rightEntries = entries
                .filter { (bytes, prefixAct) -> prefixAct.first > bitIndex && getBit(bytes, bitIndex) == 1 }

            return TrieNode(
                action = action,
                left = buildTrieNode(leftEntries, bitIndex + 1, maxBits),
                right = buildTrieNode(rightEntries, bitIndex + 1, maxBits),
            )
        }

        private fun getBit(bytes: ByteArray, bitIndex: Int): Int {
            val byteIdx = bitIndex / 8
            val bitIdx = 7 - (bitIndex % 8)
            if (byteIdx >= bytes.size) return 0
            return (bytes[byteIdx].toInt() shr bitIdx) and 1
        }

        private fun parseIPv4String(ip: String): Long? {
            return try {
                val parts = ip.trim().split(".")
                if (parts.size != 4) return null
                parts.fold(0L) { acc, part ->
                    val octet = part.toLong()
                    if (octet < 0 || octet > 255) return null
                    (acc shl 8) or octet
                }
            } catch (e: NumberFormatException) { null }
        }

        private fun longToBytes(value: Long): ByteArray = byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte(),
        )
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
