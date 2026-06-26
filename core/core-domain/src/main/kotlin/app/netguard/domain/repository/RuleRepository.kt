package app.netguard.domain.repository

import app.netguard.domain.entity.RoutingRule
import kotlinx.coroutines.flow.Flow

/**
 * RuleRepository — port for routing rule data access.
 */
interface RuleRepository {

    /** Observe all rules, ordered by priority */
    fun observeAll(): Flow<List<RoutingRule>>

    /** Observe rules in a specific group */
    fun observeByGroup(groupId: String): Flow<List<RoutingRule>>

    /** Get a specific rule by ID */
    suspend fun getById(id: String): RoutingRule?

    /** Get all enabled rules for rule engine compilation */
    suspend fun getAllEnabled(): List<RoutingRule>

    /** Save a rule (insert or update) */
    suspend fun save(rule: RoutingRule): RoutingRule

    /** Delete a rule */
    suspend fun delete(id: String)

    /** Batch import rules — transactional */
    suspend fun importRules(rules: List<RoutingRule>, replaceAll: Boolean)

    /** Export all rules */
    suspend fun exportRules(): List<RoutingRule>

    /** Enable or disable a rule without full update */
    suspend fun setEnabled(id: String, enabled: Boolean)

    /** Reorder rules by priority */
    suspend fun reorderPriorities(orderedIds: List<String>)
}
