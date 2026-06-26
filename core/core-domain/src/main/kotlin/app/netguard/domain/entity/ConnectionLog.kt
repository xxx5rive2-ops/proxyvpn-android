package app.netguard.domain.entity

import kotlinx.datetime.Instant

/**
 * ConnectionLog — immutable record of a proxied or direct connection.
 *
 * Used for:
 * - Traffic monitoring UI
 * - Audit trail
 * - Debugging rule matching
 * - Statistics aggregation
 *
 * Privacy note: This entity is only created when logging is enabled by the user.
 * By default, connection logging is OFF.
 */
data class ConnectionLog(
    val id: String,
    val timestamp: Instant,
    val appUid: Int,
    val appPackage: String?,
    val protocol: TransportProtocol,
    val destinationHost: String,
    val destinationIp: String?,
    val destinationPort: Int,
    val matchedRuleId: String?,
    val action: RuleAction,
    val proxyServerId: String?,
    val bytesUploaded: Long,
    val bytesDownloaded: Long,
    val durationMs: Long,
    val status: ConnectionStatus,
    val errorMessage: String?,
) {
    val totalBytes: Long get() = bytesUploaded + bytesDownloaded
}

enum class ConnectionStatus {
    ACTIVE,
    COMPLETED,
    BLOCKED,
    FAILED,
    TIMEOUT,
}
