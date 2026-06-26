package app.netguard.domain.repository

import app.netguard.domain.entity.ConnectionLog
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * ConnectionLogRepository — port for connection log data access.
 *
 * Privacy note: Implementations must respect the user's logging preference.
 * When logging is disabled, insert operations are no-ops.
 */
interface ConnectionLogRepository {

    /** Observe recent connection logs, limited to [limit] entries */
    fun observeRecent(limit: Int = 100): Flow<List<ConnectionLog>>

    /** Observe logs filtered by app UID */
    fun observeByApp(uid: Int, limit: Int = 50): Flow<List<ConnectionLog>>

    /** Insert a new log entry */
    suspend fun insert(log: ConnectionLog)

    /** Delete logs older than specified time */
    suspend fun deleteOlderThan(timestamp: Instant): Int

    /** Delete all logs */
    suspend fun deleteAll()

    /** Get aggregate statistics for a time period */
    suspend fun getStats(from: Instant, to: Instant): ConnectionStats
}

data class ConnectionStats(
    val totalConnections: Int,
    val blockedConnections: Int,
    val proxiedConnections: Int,
    val directConnections: Int,
    val totalBytesUploaded: Long,
    val totalBytesDownloaded: Long,
    val uniqueApps: Int,
    val uniqueHosts: Int,
)
