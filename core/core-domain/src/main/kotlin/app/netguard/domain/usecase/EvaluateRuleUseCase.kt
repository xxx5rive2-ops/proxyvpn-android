package app.netguard.domain.usecase

import app.netguard.domain.entity.RuleAction
import app.netguard.domain.entity.RoutingRule
import app.netguard.domain.entity.TransportProtocol
import app.netguard.domain.repository.RuleRepository
import javax.inject.Inject

/**
 * EvaluateRuleUseCase — encapsulates the business logic of rule matching.
 *
 * This is a PURE use case for testing rule matching without the full engine.
 * The actual high-performance matching in VPN context uses the compiled
 * rule engine in engine-rules module.
 *
 * This use case is used for:
 * - Rule testing UI (dry-run)
 * - Debugging specific connections
 * - Validating rule configurations
 */
class EvaluateRuleUseCase @Inject constructor(
    private val ruleRepository: RuleRepository,
) {
    /**
     * Evaluates what action should be taken for a given connection.
     *
     * @return RoutingDecision with the matching rule and action
     */
    suspend fun evaluate(
        appUid: Int,
        appPackage: String?,
        destinationHost: String?,
        destinationIp: String?,
        destinationPort: Int,
        protocol: TransportProtocol,
    ): RoutingDecision {
        val rules = ruleRepository.getAllEnabled()
            .sortedByDescending { it.priority }

        for (rule in rules) {
            if (matches(rule, appUid, appPackage, destinationHost, destinationIp, destinationPort, protocol)) {
                return RoutingDecision(
                    action = rule.action,
                    matchedRule = rule,
                    isExplicit = true,
                )
            }
        }

        // No rule matched — use default action (DIRECT)
        return RoutingDecision(
            action = RuleAction.Direct,
            matchedRule = null,
            isExplicit = false,
        )
    }

    private fun matches(
        rule: RoutingRule,
        appUid: Int,
        appPackage: String?,
        destinationHost: String?,
        destinationIp: String?,
        destinationPort: Int,
        protocol: TransportProtocol,
    ): Boolean {
        // Delegate to condition evaluation
        // The full compiled implementation is in engine-rules
        return evaluateCondition(
            condition = rule.condition,
            appUid = appUid,
            appPackage = appPackage,
            destinationHost = destinationHost,
            destinationIp = destinationIp,
            destinationPort = destinationPort,
            protocol = protocol,
        )
    }

    private fun evaluateCondition(
        condition: app.netguard.domain.entity.RuleCondition,
        appUid: Int,
        appPackage: String?,
        destinationHost: String?,
        destinationIp: String?,
        destinationPort: Int,
        protocol: TransportProtocol,
    ): Boolean = when (condition) {
        is app.netguard.domain.entity.RuleCondition.AppUid ->
            appUid in condition.uids

        is app.netguard.domain.entity.RuleCondition.AppPackage ->
            appPackage != null && appPackage in condition.packages

        is app.netguard.domain.entity.RuleCondition.DomainExact ->
            destinationHost?.equals(condition.domain, ignoreCase = true) == true

        is app.netguard.domain.entity.RuleCondition.DomainSuffix ->
            destinationHost?.endsWith(".${condition.suffix}", ignoreCase = true) == true ||
                destinationHost?.equals(condition.suffix, ignoreCase = true) == true

        is app.netguard.domain.entity.RuleCondition.DomainKeyword ->
            destinationHost?.contains(condition.keyword, ignoreCase = true) == true

        is app.netguard.domain.entity.RuleCondition.DomainWildcard ->
            destinationHost?.let { matchesWildcard(it, condition.pattern) } == true

        is app.netguard.domain.entity.RuleCondition.IpAddress ->
            destinationIp == condition.ip

        is app.netguard.domain.entity.RuleCondition.IpCidr ->
            destinationIp?.let { matchesCidr(it, condition.cidr) } == true

        is app.netguard.domain.entity.RuleCondition.Port ->
            destinationPort in condition.ports ||
                condition.ranges.any { destinationPort in it }

        is app.netguard.domain.entity.RuleCondition.NetworkProtocol ->
            condition.protocol == TransportProtocol.BOTH || condition.protocol == protocol

        is app.netguard.domain.entity.RuleCondition.GeoIp ->
            false // Requires GeoIP DB — handled in compiled engine

        is app.netguard.domain.entity.RuleCondition.And ->
            condition.conditions.all {
                evaluateCondition(it, appUid, appPackage, destinationHost, destinationIp, destinationPort, protocol)
            }

        is app.netguard.domain.entity.RuleCondition.Or ->
            condition.conditions.any {
                evaluateCondition(it, appUid, appPackage, destinationHost, destinationIp, destinationPort, protocol)
            }

        is app.netguard.domain.entity.RuleCondition.Final -> true
    }

    private fun matchesWildcard(domain: String, pattern: String): Boolean {
        if (!pattern.startsWith("*.")) return domain.equals(pattern, ignoreCase = true)
        val suffix = pattern.substring(1) // remove leading *
        return domain.endsWith(suffix, ignoreCase = true) && domain.length > suffix.length
    }

    private fun matchesCidr(ip: String, cidr: String): Boolean {
        return try {
            val parts = cidr.split("/")
            if (parts.size != 2) return false
            val networkAddr = ipToLong(parts[0])
            val prefixLen = parts[1].toInt()
            val ipAddr = ipToLong(ip)
            if (networkAddr < 0 || ipAddr < 0 || prefixLen < 0 || prefixLen > 32) return false
            val mask = if (prefixLen == 0) 0L else (-1L shl (32 - prefixLen)) and 0xFFFFFFFFL
            (ipAddr and mask) == (networkAddr and mask)
        } catch (e: Exception) {
            false
        }
    }

    private fun ipToLong(ip: String): Long {
        val parts = ip.split(".")
        if (parts.size != 4) return -1L
        return parts.fold(0L) { acc, part ->
            val octet = part.toLongOrNull() ?: return -1L
            if (octet < 0 || octet > 255) return -1L
            (acc shl 8) or octet
        }
    }
}

data class RoutingDecision(
    val action: RuleAction,
    val matchedRule: RoutingRule?,
    val isExplicit: Boolean,
)
