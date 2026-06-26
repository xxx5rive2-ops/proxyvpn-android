package app.netguard.domain.entity

import kotlinx.datetime.Instant

/**
 * ProxyServer — core domain entity representing a proxy server configuration.
 *
 * This is a pure Kotlin data class with NO Android dependencies.
 * It represents the business concept of a proxy server,
 * not the database row or network model.
 *
 * Sealed hierarchy allows exhaustive when-expressions at usage sites.
 */
data class ProxyServer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: ProxyProtocol,
    val authentication: ProxyAuthentication?,
    val isEnabled: Boolean,
    val priority: Int,
    val tags: Set<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val latencyMs: Long? = null,
    val lastHealthCheck: Instant? = null,
    val healthStatus: HealthStatus = HealthStatus.UNKNOWN,
) {
    init {
        require(host.isNotBlank()) { "Host cannot be blank" }
        require(port in 1..65535) { "Port must be 1-65535, got: $port" }
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(priority >= 0) { "Priority must be non-negative" }
    }

    val isHealthy: Boolean get() = healthStatus == HealthStatus.HEALTHY
    val addressString: String get() = if (host.contains(':')) "[$host]:$port" else "$host:$port"
}

enum class ProxyProtocol {
    SOCKS5,
    HTTP,
    HTTPS,
    SHADOWSOCKS,
    VMESS,
    VLESS,
    TROJAN,
    SSH,
}

sealed class ProxyAuthentication {
    data class UserPassword(
        val username: String,
        val password: String,
    ) : ProxyAuthentication() {
        init {
            require(username.isNotBlank()) { "Username cannot be blank" }
            // Password CAN be blank (some servers allow empty password)
        }
        // Override toString to never leak password in logs
        override fun toString(): String = "UserPassword(username=$username, password=***)"
    }

    data class Certificate(
        val certificatePath: String,
        val privateKeyPath: String,
        val passphrase: String?,
    ) : ProxyAuthentication() {
        override fun toString(): String = "Certificate(path=$certificatePath)"
    }
}

enum class HealthStatus {
    UNKNOWN,
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
}
