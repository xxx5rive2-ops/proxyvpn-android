package app.netguard.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "routing_rules", indices = [Index("is_enabled"), Index("priority"), Index("group_id")])
data class RoutingRuleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "condition_json") val conditionJson: String,
    @ColumnInfo(name = "action_json") val actionJson: String,
    @ColumnInfo(name = "priority") val priority: Int,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
    @ColumnInfo(name = "group_id") val groupId: String?,
    @ColumnInfo(name = "comment") val comment: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)
