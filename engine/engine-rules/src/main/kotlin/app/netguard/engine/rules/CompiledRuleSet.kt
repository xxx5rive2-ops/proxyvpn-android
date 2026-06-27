package app.netguard.engine.rules

import app.netguard.domain.entity.RuleAction
import app.netguard.domain.entity.RuleCondition
import app.netguard.domain.entity.RoutingRule
import app.netguard.domain.entity.TransportProtocol

/**
 * CompiledRuleSet — high-performance compiled representation of routing rules.
 *
 * Compiles [RoutingRule] domain objects into optimized data structures:
 * - [DomainTrie]:  O(d) domain matching
 * - [IpCidrTrie]:  O(32) IP/CIDR matching
 * - UID/Package maps: O(1) app matching
 * - Port map: O(1) port matching
 *
 * IMMUTABLE after construction — safe for concurrent reads across threads.
 * Hot-reload via AtomicReference swap at the RuleEngine level.
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
     * Evaluation order (highest priority first):
     * 1. UID → 2. Package → 3. Domain → 4. IP/CIDR → 5. Port → 6. Default
     */
    fun evaluate(
        uid: Int,
        packageName: String?,
        domain: String?,
        destIp: String,
        destPort: Int,
        protocol: TransportProtocol,
    ): EvaluationResult {
        if (uid >= 0) {
            uidActions[uid]?.let { return EvaluationResult(it, MatchType.UID) }
        }
        packageName?.let {
            packageActions[it]?.let { a -> return EvaluationResult(a, MatchType.PACKAGE) }
        }
        domain?.let {
            domainTrie.lookup(it)?.let { a -> return EvaluationResult(a, MatchType.DOMAIN) }
        }
        ipTrie.lookup(destIp)?.let { return EvaluationResult(it, MatchType.IP_CIDR) }
        portActions[destPort]?.let { return EvaluationResult(it, MatchType.PORT) }
        return EvaluationResult(defaultAction, MatchType.DEFAULT)
    }

    data class EvaluationResult(val action: RuleAction, val matchType: MatchType)

    enum class MatchType { UID, PACKAGE, DOMAIN, IP_CIDR, GEOIP, PORT, DEFAULT }

    /**
     * Compiler — transforms [RoutingRule] list into [CompiledRuleSet].
     */
    class Compiler {

        fun compile(rules: List<RoutingRule>): CompiledRuleSet {
            val domainBuilder = DomainTrie.builder()
            val ipBuilder = IpCidrTrie.builder()
            val uidActions = HashMap<Int, RuleAction>()
            val packageActions = HashMap<String, RuleAction>()
            val portActions = HashMap<Int, RuleAction>()
            var defaultAction: RuleAction = RuleAction.Direct

            // Sort ascending so higher priority overwrites lower
            val sorted = rules.filter { it.isEnabled }.sortedBy { it.priority }

            for (rule in sorted) {
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

            return CompiledRuleSet(
                domainTrie = domainBuilder.build(),
                ipTrie = ipBuilder.build(),
                uidActions = uidActions,
                packageActions = packageActions,
                portActions = portActions,
                defaultAction = defaultAction,
                ruleCount = sorted.size,
                compiledAtNanos = System.nanoTime(),
            )
        }

        private fun compileCondition(
            condition: RuleCondition,
            action: RuleAction,
            domainBuilder: DomainTrie.Builder,
            ipBuilder: IpCidrTrie.Builder,
            uidActions: HashMap<Int, RuleAction>,
            packageActions: HashMap<String, RuleAction>,
            portActions: HashMap<Int, RuleAction>,
            onDefault: () -> Unit,
        ) {
            when (condition) {
                is RuleCondition.AppUid ->
                    condition.uids.forEach { uidActions[it] = action }
                is RuleCondition.AppPackage ->
                    condition.packages.forEach { packageActions[it] = action }
                is RuleCondition.DomainExact ->
                    domainBuilder.addExact(condition.domain, action)
                is RuleCondition.DomainSuffix ->
                    domainBuilder.addSuffix(condition.suffix, action)
                is RuleCondition.DomainWildcard ->
                    domainBuilder.addWildcard(condition.pattern, action)
                is RuleCondition.DomainKeyword -> { /* TODO: keyword index */ }
                is RuleCondition.IpAddress ->
                    ipBuilder.addIp(condition.ip, action)
                is RuleCondition.IpCidr ->
                    ipBuilder.addCidr(condition.cidr, action)
                is RuleCondition.Port ->
                    condition.ports.forEach { portActions[it] = action }
                is RuleCondition.GeoIp -> { /* TODO: GeoIP DB */ }
                is RuleCondition.NetworkProtocol -> { /* applied at runtime */ }
                is RuleCondition.And ->
                    condition.conditions.forEach {
                        compileCondition(it, action, domainBuilder, ipBuilder,
                            uidActions, packageActions, portActions, onDefault)
                    }
                is RuleCondition.Or ->
                    condition.conditions.forEach {
                        compileCondition(it, action, domainBuilder, ipBuilder,
                            uidActions, packageActions, portActions, onDefault)
                    }
                is RuleCondition.Final -> onDefault()
            }
        }
    }
}
