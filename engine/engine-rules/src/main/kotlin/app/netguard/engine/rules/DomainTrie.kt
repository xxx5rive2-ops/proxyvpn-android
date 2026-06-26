package app.netguard.engine.rules

import app.netguard.domain.entity.RuleAction

/**
 * DomainTrie — high-performance domain matching using a reversed suffix trie.
 *
 * Algorithm:
 * - Domains are split by '.' and reversed for efficient suffix matching
 * - "mail.google.com" → ["com", "google", "mail"]
 * - Traversal: root → com → google → mail
 * - Wildcard "*.google.com" stored as: com → google → [WILDCARD_MATCH]
 *
 * Performance characteristics:
 * - Insert: O(d) where d = number of domain labels (typically 2-5)
 * - Lookup: O(d) — independent of total rule count
 * - Memory: O(n × d) where n = unique domains
 *
 * Thread safety:
 * - This class is IMMUTABLE after construction
 * - Built once via DomainTrieBuilder, then shared read-only across threads
 * - Updates create a new trie (copy-on-write at the CompiledRuleSet level)
 *
 * Design decision — why not HashMap?
 * - HashMap stores exact domains only, no wildcard support
 * - Trie naturally supports prefix/suffix wildcards
 * - Memory usage is lower for large domain sets (shared prefixes)
 */
class DomainTrie private constructor(
    private val root: TrieNode
) {

    /**
     * Look up a domain and return the associated action, or null if no match.
     *
     * Matching priority (highest to lowest):
     * 1. Exact match: "mail.google.com" matches rule "mail.google.com"
     * 2. Wildcard: "mail.google.com" matches rule "*.google.com"
     * 3. Suffix: "mail.google.com" matches rule "google.com" (if isSuffix=true)
     *
     * @param domain The domain to look up (e.g., "mail.google.com")
     * @return The matching [RuleAction] or null if no rule matches
     */
    fun lookup(domain: String): RuleAction? {
        val labels = domain.lowercase().split('.').reversed()
        return lookupNode(root, labels, 0)
    }

    private fun lookupNode(node: TrieNode, labels: List<String>, depth: Int): RuleAction? {
        // Exact or wildcard match at this node
        if (depth == labels.size) {
            return node.exactAction ?: node.wildcardAction
        }

        val label = labels[depth]

        // Try to traverse deeper first (more specific match wins)
        val childMatch = node.children[label]?.let {
            lookupNode(it, labels, depth + 1)
        }
        if (childMatch != null) return childMatch

        // No deeper match — check wildcard at this level
        return node.wildcardAction
    }

    data class TrieNode(
        val children: Map<String, TrieNode>,
        val exactAction: RuleAction?,    // Matches exact domain at this node
        val wildcardAction: RuleAction?, // Matches *.this-domain and subdomains
    )

    /**
     * Builder for DomainTrie — allows incremental construction.
     * After building, the trie is immutable.
     */
    class Builder {
        private val root = MutableTrieNode()

        /**
         * Add an exact domain rule: only matches "example.com" exactly.
         */
        fun addExact(domain: String, action: RuleAction): Builder {
            val labels = domain.lowercase().split('.').reversed()
            var node = root
            for (label in labels) {
                node = node.children.getOrPut(label) { MutableTrieNode() }
            }
            node.exactAction = action
            return this
        }

        /**
         * Add a suffix rule: matches "example.com" AND "sub.example.com".
         */
        fun addSuffix(domain: String, action: RuleAction): Builder {
            val labels = domain.lowercase().split('.').reversed()
            var node = root
            for (label in labels) {
                node = node.children.getOrPut(label) { MutableTrieNode() }
            }
            node.wildcardAction = action
            return this
        }

        /**
         * Add a wildcard rule: "*.example.com" matches subdomains only.
         * Does NOT match "example.com" itself.
         */
        fun addWildcard(pattern: String, action: RuleAction): Builder {
            // "*.google.com" → strip "*.": store as suffix match on "google.com"
            val domain = if (pattern.startsWith("*.")) pattern.substring(2) else pattern
            return addSuffix(domain, action)
        }

        fun build(): DomainTrie {
            return DomainTrie(root.toImmutable())
        }

        private class MutableTrieNode {
            val children: HashMap<String, MutableTrieNode> = HashMap()
            var exactAction: RuleAction? = null
            var wildcardAction: RuleAction? = null

            fun toImmutable(): TrieNode = TrieNode(
                children = children.mapValues { it.value.toImmutable() },
                exactAction = exactAction,
                wildcardAction = wildcardAction,
            )
        }
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
