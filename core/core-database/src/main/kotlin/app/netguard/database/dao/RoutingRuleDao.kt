package app.netguard.database.dao

import androidx.room.*
import app.netguard.database.entity.RoutingRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutingRuleDao {
    @Query("SELECT * FROM routing_rules ORDER BY priority DESC")
    fun observeAll(): Flow<List<RoutingRuleEntity>>

    @Query("SELECT * FROM routing_rules WHERE group_id = :groupId ORDER BY priority DESC")
    fun observeByGroup(groupId: String): Flow<List<RoutingRuleEntity>>

    @Query("SELECT * FROM routing_rules WHERE id = :id")
    suspend fun getById(id: String): RoutingRuleEntity?

    @Query("SELECT * FROM routing_rules WHERE is_enabled = 1 ORDER BY priority ASC")
    suspend fun getAllEnabled(): List<RoutingRuleEntity>

    @Upsert
    suspend fun upsert(entity: RoutingRuleEntity)

    @Upsert
    suspend fun upsertAll(entities: List<RoutingRuleEntity>)

    @Query("DELETE FROM routing_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM routing_rules")
    suspend fun deleteAll()

    @Query("UPDATE routing_rules SET is_enabled = :enabled, updated_at_ms = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)
}
