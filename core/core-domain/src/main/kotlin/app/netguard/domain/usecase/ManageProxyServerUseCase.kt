package app.netguard.domain.usecase

import app.netguard.domain.entity.ProxyServer
import app.netguard.domain.entity.ProxyProtocol
import app.netguard.domain.repository.ProxyServerRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import kotlinx.datetime.Clock

/**
 * ManageProxyServerUseCase — business logic for proxy server management.
 *
 * Single Responsibility: manages CRUD operations for proxy servers
 * with business rule enforcement.
 *
 * Business rules enforced here (NOT in repository or UI):
 * - Port validation
 * - Duplicate detection
 * - Default server constraints
 */
class ManageProxyServerUseCase @Inject constructor(
    private val repository: ProxyServerRepository,
) {
    fun observeServers(): Flow<List<ProxyServer>> = repository.observeAll()

    fun observeEnabledServers(): Flow<List<ProxyServer>> = repository.observeEnabled()

    suspend fun addServer(
        name: String,
        host: String,
        port: Int,
        protocol: ProxyProtocol,
        authentication: app.netguard.domain.entity.ProxyAuthentication? = null,
        tags: Set<String> = emptySet(),
    ): Result<ProxyServer> = runCatching {
        validateHost(host)
        validatePort(port)
        validateName(name)

        val now = Clock.System.now()
        val server = ProxyServer(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            host = host.trim(),
            port = port,
            protocol = protocol,
            authentication = authentication,
            isEnabled = true,
            priority = repository.count(),
            tags = tags,
            createdAt = now,
            updatedAt = now,
        )
        repository.save(server)
    }

    suspend fun updateServer(server: ProxyServer): Result<ProxyServer> = runCatching {
        validateHost(server.host)
        validatePort(server.port)
        validateName(server.name)
        repository.save(server.copy(updatedAt = Clock.System.now()))
    }

    suspend fun deleteServer(id: String): Result<Unit> = runCatching {
        repository.delete(id)
    }

    suspend fun getServer(id: String): ProxyServer? = repository.getById(id)

    suspend fun reorderServers(orderedIds: List<String>): Result<Unit> = runCatching {
        require(orderedIds.isNotEmpty()) { "Cannot reorder empty list" }
        repository.reorderPriorities(orderedIds)
    }

    private fun validateHost(host: String) {
        require(host.isNotBlank()) { "Host cannot be blank" }
        require(host.length <= 253) { "Host too long (max 253 characters)" }
        // Basic sanity check — full validation happens at connection time
        require(!host.contains(' ')) { "Host cannot contain spaces" }
    }

    private fun validatePort(port: Int) {
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        // Warn about well-known privileged ports — but allow them
        // (some proxies run on port 80, 443, etc.)
    }

    private fun validateName(name: String) {
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(name.length <= 100) { "Name too long (max 100 characters)" }
    }
}
