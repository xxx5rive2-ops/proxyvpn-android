package app.netguard.database.dao

import androidx.room.*
import app.netguard.database.entity.ProxyServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyServerDao {
    @Query("SELECT * FROM proxy_servers ORDER BY priority ASC")
    fun observeAll(): Flow<List<ProxyServerEntity>>

    @Query("SELECT * FROM proxy_servers WHERE is_enabled = 1 ORDER BY priority ASC")
    fun observeEnabled(): Flow<List<ProxyServerEntity>>

    @Query("SELECT * FROM proxy_servers WHERE id = :id")
    suspend fun getById(id: String): ProxyServerEntity?

    @Upsert
    suspend fun upsert(entity: ProxyServerEntity)

    @Query("DELETE FROM proxy_servers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM proxy_servers")
    suspend fun count(): Int

    @Query("UPDATE proxy_servers SET latency_ms = :latencyMs, health_status = :status, last_health_check_ms = :checkedAt WHERE id = :id")
    suspend fun updateHealth(id: String, status: String, latencyMs: Long?, checkedAt: Long)
}
