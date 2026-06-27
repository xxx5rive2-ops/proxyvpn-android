package app.netguard.domain.usecase

import app.netguard.domain.entity.RuleAction
import app.netguard.domain.entity.RuleCondition
import app.netguard.domain.entity.RoutingRule
import app.netguard.domain.entity.TransportProtocol
import app.netguard.domain.repository.RuleRepository
import javax.inject.Inject

/**
 * EvaluateRuleUseCase — rule matching for dry-run / testing UI.
 * High-performance matching at VPN time uses CompiledRuleSet in engine-rules.
 */
class EvaluateRuleUseCase @Inject constructor(
    private val ruleRepository: RuleRepository,
) {
    suspend fun evaluate(
        appUid: Int,
        appPackage: String?,
        destinationHost: String?,
        destinationIp: String?,
        destinationPort: Int,
        protocol: TransportProtocol,
    ): RoutingDecision {
        val rules = ruleRepository.getAllEnabled().sortedByDescending { it.priority }
        for (rule in rules) {
            if (matches(rule, appUid, appPackage, destinationHost, destinationIp, destinationPort, protocol)) {
                return RoutingDecision(action = rule.action, matchedRule = rule, isExplicit = true)
            }
        }
        return RoutingDecision(action = RuleAction.Direct, matchedRule = null, isExplicit = false)
    }

    private fun matches(
        rule: RoutingRule,
        appUid: Int,
        appPackage: String?,
        destinationHost: String?,
        destinationIp: String?,
        destinationPort: Int,
        protocol: TransportProtocol,
    ): Boolean = evaluateCondition(rule.condition, appUid, appPackage,
        destinationHost, destinationIp, destinationPort, protocol)

    private fun evaluateCondition(
        condition: RuleCondition,
        appUid: Int,
        appPackage: String?,
        destinationHost: String?,
        destinationIp: String?,
        destinationPort: Int,
        protocol: TransportProtocol,
    ): Boolean = when (condition) {
        is RuleCondition.AppUid          -> appUid in condition.uids
        is RuleCondition.AppPackage      -> appPackage != null && appPackage in condition.packages
        is RuleCondition.DomainExact     -> destinationHost?.equals(condition.domain, ignoreCase = true) == true
        is RuleCondition.DomainSuffix    ->
            destinationHost?.endsWith(".${condition.suffix}", ignoreCase = true) == true ||
            destinationHost?.equals(condition.suffix, ignoreCase = true) == true
        is RuleCondition.DomainKeyword   -> destinationHost?.contains(condition.keyword, ignoreCase = true) == true
        is RuleCondition.DomainWildcard  -> destinationHost?.let { matchesWildcard(it, condition.pattern) } == true
        is RuleCondition.IpAddress       -> destinationIp == condition.ip
        is RuleCondition.IpCidr          -> destinationIp?.let { matchesCidr(it, condition.cidr) } == true
        is RuleCondition.Port            -> destinationPort in condition.ports ||
                                           condition.ranges.any { destinationPort in it }
        is RuleCondition.NetworkProtocol -> condition.protocol == TransportProtocol.BOTH || condition.protocol == protocol
        is RuleCondition.GeoIp          -> false
        is RuleCondition.And            -> condition.conditions.all {
            evaluateCondition(it, appUid, appPackage, destinationHost, destinationIp, destinationPort, protocol)
        }
        is RuleCondition.Or             -> condition.conditions.any {
            evaluateCondition(it, appUid, appPackage, destinationHost, destinationIp, destinationPort, protocol)
        }
        is RuleCondition.Final          -> true
    }

    private fun matchesWildcard(domain: String, pattern: String): Boolean {
        if (!pattern.startsWith("*.")) return domain.equals(pattern, ignoreCase = true)
        val suffix = pattern.substring(1)
        return domain.endsWith(suffix, ignoreCase = true) && domain.length > suffix.length
    }

    private fun matchesCidr(ip: String, cidr: String): Boolean {
        return try {
            val (addr, prefix) = cidr.split("/")
            val prefixLen = prefix.toInt()
            val networkInt = ipToLong(addr) ?: return false
            val ipInt = ipToLong(ip) ?: return false
            if (prefixLen !in 0..32) return false
            val mask = if (prefixLen == 0) 0L else ((-1L shl (32 - prefixLen)) and 0xFFFFFFFFL)
            (ipInt and mask) == (networkInt and mask)
        } catch (e: Exception) { false }
    }

    private fun ipToLong(ip: String): Long? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        return parts.fold(0L) { acc, part ->
            val octet = part.toLongOrNull() ?: return null
            if (octet !in 0..255) return null
            (acc shl 8) or octet
        }
    }
}

data class RoutingDecision(
    val action: RuleAction,
    val matchedRule: RoutingRule?,
    val isExplicit: Boolean,
)
