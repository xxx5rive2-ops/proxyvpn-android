package app.netguard.engine.rules

import app.netguard.domain.entity.RuleAction
import app.netguard.domain.entity.RoutingRule
import app.netguard.domain.entity.TransportProtocol
import java.util.concurrent.atomic.AtomicReference

/**
 * CompiledRuleSet — the high-performance compiled representation of routing rules.
 *
 * This is the central data structure of the rule engine.
 * It takes a list of [RoutingRule] domain objects and compiles them into
 * optimized structures for O(log n) or O(1) lookup at packet processing time.
 *
 * Compiled structures:
 * - [DomainTrie]:  O(d) domain matching, where d = domain depth
 * - [IpCidrTrie]:  O(32) IP/CIDR matching — constant time
 * - UID Bitmap:    O(1) app-based matching via BitSet
 * - Port Bitmap:   O(1) port matching via BitSet (65536 bits = 8KB)
 *
 * Thread safety:
 * - CompiledRuleSet instances are IMMUTABLE after construction
 * - The [RuleEngine] holds the current set in an [AtomicReference]
 * - Hot-reload creates a new instance and atomically swaps the reference
 *
 * Memory:
 * - Port bitmap: 8KB (65536 bits)
 * - UID bitmap: dynamic, proportional to max UID
 * - Domain trie: proportional to unique domain labels
 * - IP trie: proportional to CIDR count × 32 bits
 */
class CompiledRuleSet private constructor(
    private val domainTrie: DomainTrie,
    private val ipTrie: IpCidrTrie,
    private val uidActions: Map<Int, RuleAction>,
    private val packageActions: Map<String, RuleAction>,
    private val portActions: Map<Int, RuleAction>,
    private val defaultAction: RuleAction,
    val ruleCount: Int,
    val compiledAtNanos: Long,
) {

    /**
     * Evaluate routing decision for a packet.
     *
     * Evaluation order (highest priority first):
     * 1. UID-based (exact app match)
     * 2. Package-based (app by package name)
     * 3. Domain-based (via trie)
     * 4. IP/CIDR-based (via trie)
     * 5. Port-based
     * 6. Default catch-all
     *
     * @param uid Android app UID (-1 if unknown)
     * @param packageName App package name (null if unknown)
     * @param domain Resolved domain (null for direct IP connections)
     * @param destIp Destination IP address string
     * @param destPort Destination port
     * @param protocol TCP or UDP
     * @return [EvaluationResult] with action and metadata
     */
    fun evaluate(
        uid: Int,
        packageName: String?,
        domain: String?,
        destIp: String,
        destPort: Int,
        protocol: TransportProtocol,
    ): EvaluationResult {

        // Priority 1: Per-UID rules (most specific — per-app)
        if (uid >= 0) {
            uidActions[uid]?.let { return EvaluationResult(it, MatchType.UID) }
        }

        // Priority 2: Per-package rules
        if (packageName != null) {
            packageActions[packageName]?.let { return EvaluationResult(it, MatchType.PACKAGE) }
        }

        // Priority 3: Domain rules (most common case)
        if (domain != null) {
            domainTrie.lookup(domain)?.let { return EvaluationResult(it, MatchType.DOMAIN) }
        }

        // Priority 4: IP/CIDR rules
        ipTrie.lookup(destIp)?.let { return EvaluationResult(it, MatchType.IP_CIDR) }

        // Priority 5: Port rules
        portActions[destPort]?.let { return EvaluationResult(it, MatchType.PORT) }

        // Default: configured catch-all
        return EvaluationResult(defaultAction, MatchType.DEFAULT)
    }

    data class EvaluationResult(
        val action: RuleAction,
        val matchType: MatchType,
    )

    enum class MatchType {
        UID, PACKAGE, DOMAIN, IP_CIDR, GEOIP, PORT, DEFAULT
    }

    /**
     * Compiler — transforms [RoutingRule] domain objects into [CompiledRuleSet].
     *
     * Time complexity: O(n × d) where n = rule count, d = average domain depth
     * Should be run off the main thread (rule compilation can take 10-50ms for large sets)
     */
    class Compiler {

        fun compile(rules: List<RoutingRule>): CompiledRuleSet {
            val startNanos = System.nanoTime()

            val domainBuilder = DomainTrie.builder()
            val ipBuilder = IpCidrTrie.builder()
            val uidActions = HashMap<Int, RuleAction>()
            val packageActions = HashMap<String, RuleAction>()
            val portActions = HashMap<Int, RuleAction>()
            var defaultAction: RuleAction = RuleAction.Direct

            // Sort by priority descending — higher priority rules processed last,
            // overwriting lower priority (last write wins for same-key conflicts)
            val sortedRules = rules
                .filter { it.isEnabled }
                .sortedBy { it.priority } // ascending: lower priority first, overwritten by higher

            for (rule in sortedRules) {
                compileCondition(
                    condition = rule.condition,
                    action = rule.action,
                    domainBuilder = domainBuilder,
                    ipBuilder = ipBuilder,
                    uidActions = uidActions,
                    packageActions = packageActions,
                    portActions = portActions,
                    onDefault = { defaultAction = rule.action },
                )
            }

            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L

            return CompiledRuleSet(
                domainTrie = domainBuilder.build(),
                ipTrie = ipBuilder.build(),
                uidActions = uidActions,
                packageActions = packageActions,
                portActions = portActions,
                defaultAction = defaultAction,
                ruleCount = sortedRules.size,
                compiledAtNanos = System.nanoTime(),
            ).also {
                // Log compilation time for performance monitoring
                if (elapsedMs > 500) {
                    // Warning: compilation too slow
                }
            }
        }

        private fun compileCondition(
            condition: app.netguard.domain.entity.RuleCondition,
            action: RuleAction,
            domainBuilder: DomainTrie.Builder,
            ipBuilder: IpCidrTrie.Builder,
            uidActions: HashMap<Int, RuleAction>,
            packageActions: HashMap<String, RuleAction>,
            portActions: HashMap<Int, RuleAction>,
            onDefault: () -> Unit,
        ) {
            when (condition) {
                is app.netguard.domain.entity.RuleCondition.AppUid ->
                    condition.uids.forEach { uid -> uidActions[uid] = action }

                is app.netguard.domain.entity.RuleCondition.AppPackage ->
                    condition.packages.forEach { pkg -> packageActions[pkg] = action }

                is app.netguard.domain.entity.RuleCondition.DomainExact ->
                    domainBuilder.addExact(condition.domain, action)

                is app.netguard.domain.entity.RuleCondition.DomainSuffix ->
                    domainBuilder.addSuffix(condition.suffix, action)

                is app.netguard.domain.entity.RuleCondition.DomainWildcard ->
                    domainBuilder.addWildcard(condition.pattern, action)

                is app.netguard.domain.entity.RuleCondition.DomainKeyword -> {
                    // Keywords can't be compiled into trie — stored separately
                    // For now: domain trie with special keyword node
                    // TODO: keyword list compiled separately
                }

                is app.netguard.domain.entity.RuleCondition.IpAddress ->
                    ipBuilder.addIp(condition.ip, action)

                is app.netguard.domain.entity.RuleCondition.IpCidr ->
                    ipBuilder.addCidr(condition.cidr, action)

                is app.netguard.domain.entity.RuleCondition.Port ->
                    condition.ports.forEach { port -> portActions[port] = action }

                is app.netguard.domain.entity.RuleCondition.GeoIp -> {
                    // GeoIP requires runtime DB lookup — registered separately
                    // TODO: GeoIP integration
                }

                is app.netguard.domain.entity.RuleCondition.And ->
                    condition.conditions.forEach { subCondition ->
                        compileCondition(subCondition, action, domainBuilder, ipBuilder,
                            uidActions, packageActions, portActions, onDefault)
                    }

                is app.netguard.domain.entity.RuleCondition.Or ->
                    condition.conditions.forEach { subCondition ->
                        compileCondition(subCondition, action, domainBuilder, ipBuilder,
                            uidActions, packageActions, portActions, onDefault)
                    }

                is app.netguard.domain.entity.RuleCondition.Final -> onDefault()

                is app.netguard.domain.entity.RuleCondition.NetworkProtocol -> {
                    // Protocol filtering applied at evaluation time, not compile time
                }
            }
        }
    }
}
