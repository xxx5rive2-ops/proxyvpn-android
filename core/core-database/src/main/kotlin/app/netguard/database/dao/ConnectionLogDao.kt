package app.netguard.database.dao

import androidx.room.*
import app.netguard.database.entity.ConnectionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionLogDao {
    @Query("SELECT * FROM connection_logs ORDER BY timestamp_ms DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ConnectionLogEntity>>

    @Query("SELECT * FROM connection_logs WHERE app_uid = :uid ORDER BY timestamp_ms DESC LIMIT :limit")
    fun observeByApp(uid: Int, limit: Int): Flow<List<ConnectionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConnectionLogEntity)

    @Query("DELETE FROM connection_logs WHERE timestamp_ms < :timestampMs")
    suspend fun deleteOlderThan(timestampMs: Long): Int

    @Query("DELETE FROM connection_logs")
    suspend fun deleteAll()

    @Query("""
        SELECT 
            COUNT(*) as total,
            SUM(bytes_uploaded) as uploadedBytes,
            SUM(bytes_downloaded) as downloadedBytes,
            COUNT(DISTINCT app_uid) as uniqueApps,
            COUNT(DISTINCT destination_host) as uniqueHosts
        FROM connection_logs 
        WHERE timestamp_ms BETWEEN :fromMs AND :toMs
    """)
    suspend fun getStats(fromMs: Long, toMs: Long): ConnectionStatsRow
}

data class ConnectionStatsRow(
    val total: Int,
    val uploadedBytes: Long?,
    val downloadedBytes: Long?,
    val uniqueApps: Int,
    val uniqueHosts: Int,
)
