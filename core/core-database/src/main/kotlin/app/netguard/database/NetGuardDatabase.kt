package app.netguard.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.netguard.database.converter.DateTimeConverters
import app.netguard.database.converter.JsonConverters
import app.netguard.database.dao.ConnectionLogDao
import app.netguard.database.dao.ProxyServerDao
import app.netguard.database.dao.RoutingRuleDao
import app.netguard.database.entity.ConnectionLogEntity
import app.netguard.database.entity.ProxyServerEntity
import app.netguard.database.entity.RoutingRuleEntity

/**
 * NetGuardDatabase — Room database definition.
 *
 * Schema version strategy:
 * - ALWAYS provide Migration objects — never use fallbackToDestructiveMigration in production
 * - Migrations are tested in MigrationTest
 * - Schema exports are committed to version control for audit trail
 *
 * Security:
 * - WAL mode for better concurrent read performance
 * - Database file protected by Android file encryption (FBE)
 * - No sensitive plaintext data — credentials stored in Keystore-backed encrypted store
 */
@Database(
    entities = [
        ProxyServerEntity::class,
        RoutingRuleEntity::class,
        ConnectionLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DateTimeConverters::class, JsonConverters::class)
abstract class NetGuardDatabase : RoomDatabase() {

    abstract fun proxyServerDao(): ProxyServerDao
    abstract fun routingRuleDao(): RoutingRuleDao
    abstract fun connectionLogDao(): ConnectionLogDao

    companion object {
        const val DATABASE_NAME = "netguard.db"

        @Volatile
        private var instance: NetGuardDatabase? = null

        fun getInstance(context: Context): NetGuardDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): NetGuardDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                NetGuardDatabase::class.java,
                DATABASE_NAME,
            )
                .addCallback(DatabaseCallback())
                .enableMultiInstanceInvalidation()
                // WAL mode: better read concurrency, safer for VPN service writes
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                // Add migrations as the schema evolves:
                // .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }

        // Future migrations:
        // val MIGRATION_1_2 = object : Migration(1, 2) {
        //     override fun migrate(db: SupportSQLiteDatabase) {
        //         db.execSQL("ALTER TABLE proxy_servers ADD COLUMN notes TEXT")
        //     }
        // }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Seed default rules on first install
            // Default: bypass LAN, route everything else direct (safe default)
        }
    }
}
