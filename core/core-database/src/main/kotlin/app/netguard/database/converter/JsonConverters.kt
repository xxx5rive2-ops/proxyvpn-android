package app.netguard.database.converter

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class JsonConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringSet(value: Set<String>): String = json.encodeToString(value.toList())
    @TypeConverter
    fun toStringSet(value: String): Set<String> = json.decodeFromString<List<String>>(value).toSet()
}
