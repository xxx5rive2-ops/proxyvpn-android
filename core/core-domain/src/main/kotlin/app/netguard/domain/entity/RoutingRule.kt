package app.netguard.domain.entity

import kotlinx.datetime.Instant

/**
 * RoutingRule — core domain entity for traffic routing decisions.
 *
 * Rules are evaluated in priority order.
 * Each rule defines:
 * - What to match (condition)
 * - What to do when matched (action)
 * - In what order to evaluate (priority)
 *
 * The rule engine compiles these into efficient data structures
 * (Trie, Bitmap, etc.) for O(log n) evaluation.
 */
data class RoutingRule(
    val id: String,
    val name: String,
    val condition: RuleCondition,
    val action: RuleAction,
    val priority: Int,
    val isEnabled: Boolean,
    val groupId: String?,
    val comment: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * RuleCondition — what traffic should match this rule.
 *
 * Multiple conditions can be combined with AND logic.
 * Sealed class ensures exhaustive matching at compile time.
 */
sealed class RuleCondition {

    /** Match traffic from specific app(s) by Android UID */
    data class AppUid(val uids: Set<Int>) : RuleCondition() {
        init { require(uids.isNotEmpty()) { "UIDs set cannot be empty" } }
    }

    /** Match traffic from specific package name(s) */
    data class AppPackage(val packages: Set<String>) : RuleCondition() {
        init { require(packages.isNotEmpty()) { "Packages set cannot be empty" } }
    }

    /** Match exact domain name */
    data class DomainExact(val domain: String) : RuleCondition() {
        init { require(domain.isNotBlank()) { "Domain cannot be blank" } }
    }

    /** Match domain with wildcard (e.g., *.google.com) */
    data class DomainWildcard(val pattern: String) : RuleCondition() {
        init { require(pattern.contains('*')) { "Wildcard pattern must contain *" } }
    }

    /** Match domain suffix (e.g., google.com matches mail.google.com) */
    data class DomainSuffix(val suffix: String) : RuleCondition() {
        init { require(suffix.isNotBlank()) { "Suffix cannot be blank" } }
    }

    /** Match keywords in domain (e.g., "google" matches google.com, google.co.uk) */
    data class DomainKeyword(val keyword: String) : RuleCondition() {
        init { require(keyword.isNotBlank()) { "Keyword cannot be blank" } }
    }

    /** Match exact IP address */
    data class IpAddress(val ip: String) : RuleCondition() {
        init { require(ip.isNotBlank()) { "IP cannot be blank" } }
    }

    /** Match IP CIDR range (e.g., 192.168.0.0/24) */
    data class IpCidr(val cidr: String) : RuleCondition() {
        init { require(cidr.contains('/')) { "CIDR must contain / (e.g. 192.168.0.0/24)" } }
    }

    /** Match by country code (requires GeoIP database) */
    data class GeoIp(val countryCodes: Set<String>) : RuleCondition() {
        init {
            require(countryCodes.isNotEmpty()) { "Country codes cannot be empty" }
            require(countryCodes.all { it.length == 2 }) { "Country codes must be 2 chars (ISO 3166-1)" }
        }
    }

    /** Match specific port or port range */
    data class Port(val ports: Set<Int>, val ranges: List<IntRange> = emptyList()) : RuleCondition() {
        init {
            ports.forEach { require(it in 1..65535) { "Invalid port: $it" } }
            ranges.forEach {
                require(it.first in 1..65535) { "Invalid port range start: ${it.first}" }
                require(it.last in 1..65535) { "Invalid port range end: ${it.last}" }
            }
        }
    }

    /** Match protocol (TCP, UDP, or both) */
    data class NetworkProtocol(val protocol: TransportProtocol) : RuleCondition()

    /** Logical AND of multiple conditions */
    data class And(val conditions: List<RuleCondition>) : RuleCondition() {
        init { require(conditions.size >= 2) { "AND requires at least 2 conditions" } }
    }

    /** Logical OR of multiple conditions */
    data class Or(val conditions: List<RuleCondition>) : RuleCondition() {
        init { require(conditions.size >= 2) { "OR requires at least 2 conditions" } }
    }

    /** Catch-all — matches everything */
    object Final : RuleCondition()
}

/**
 * RuleAction — what to do when a rule matches.
 */
sealed class RuleAction {
    /** Route through specified proxy server */
    data class Proxy(val serverId: String) : RuleAction()

    /** Connect directly, bypassing proxy */
    object Direct : RuleAction()

    /** Block the connection entirely */
    object Block : RuleAction()

    /** Use DNS resolution and re-evaluate (for Fake-IP mode) */
    object Resolve : RuleAction()
}

enum class TransportProtocol { TCP, UDP, BOTH }
