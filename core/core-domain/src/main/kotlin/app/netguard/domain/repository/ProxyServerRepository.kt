package app.netguard.domain.repository

import app.netguard.domain.entity.HealthStatus
import app.netguard.domain.entity.ProxyServer
import kotlinx.coroutines.flow.Flow

/**
 * ProxyServerRepository — port (interface) for proxy server data access.
 *
 * Follows the Repository pattern from Clean Architecture.
 * Domain layer depends on this interface, NOT on the implementation.
 * Implementation lives in core-data module.
 *
 * All operations are suspending or return Flow for reactive updates.
 * Error handling: implementations throw typed exceptions,
 * callers handle via sealed Result/Either types.
 */
interface ProxyServerRepository {

    /** Observe all servers — emits on every change */
    fun observeAll(): Flow<List<ProxyServer>>

    /** Observe enabled servers only */
    fun observeEnabled(): Flow<List<ProxyServer>>

    /** Get a specific server by ID. Returns null if not found. */
    suspend fun getById(id: String): ProxyServer?

    /** Insert or update a server. Returns the saved entity. */
    suspend fun save(server: ProxyServer): ProxyServer

    /** Delete a server. Throws if server is referenced by active rules. */
    suspend fun delete(id: String)

    /** Update health status and latency after health check */
    suspend fun updateHealth(id: String, status: HealthStatus, latencyMs: Long?)

    /** Reorder servers by updating their priority values */
    suspend fun reorderPriorities(orderedIds: List<String>)

    /** Count total servers */
    suspend fun count(): Int
}
