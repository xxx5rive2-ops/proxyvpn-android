package app.netguard.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "connection_logs", indices = [Index("timestamp_ms"), Index("app_uid"), Index("status")])
data class ConnectionLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
    @ColumnInfo(name = "app_uid") val appUid: Int,
    @ColumnInfo(name = "app_package") val appPackage: String?,
    @ColumnInfo(name = "protocol") val protocol: String,
    @ColumnInfo(name = "destination_host") val destinationHost: String,
    @ColumnInfo(name = "destination_ip") val destinationIp: String?,
    @ColumnInfo(name = "destination_port") val destinationPort: Int,
    @ColumnInfo(name = "matched_rule_id") val matchedRuleId: String?,
    @ColumnInfo(name = "action_json") val actionJson: String,
    @ColumnInfo(name = "proxy_server_id") val proxyServerId: String?,
    @ColumnInfo(name = "bytes_uploaded") val bytesUploaded: Long,
    @ColumnInfo(name = "bytes_downloaded") val bytesDownloaded: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
)
