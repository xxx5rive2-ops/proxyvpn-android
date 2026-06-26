package app.netguard.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "proxy_servers", indices = [Index("is_enabled"), Index("priority")])
data class ProxyServerEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "host") val host: String,
    @ColumnInfo(name = "port") val port: Int,
    @ColumnInfo(name = "protocol") val protocol: String,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
    @ColumnInfo(name = "priority") val priority: Int,
    @ColumnInfo(name = "tags_json") val tagsJson: String,
    @ColumnInfo(name = "credential_ref") val credentialRef: String?,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
    @ColumnInfo(name = "latency_ms") val latencyMs: Long?,
    @ColumnInfo(name = "health_status") val healthStatus: String,
    @ColumnInfo(name = "last_health_check_ms") val lastHealthCheckMs: Long?,
)
