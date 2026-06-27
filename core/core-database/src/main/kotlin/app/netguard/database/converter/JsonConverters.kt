package app.netguard.database.converter

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class JsonConverters {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromStringSet(value: Set<String>): String =
        json.encodeToString(listSerializer, value.toList())

    @TypeConverter
    fun toStringSet(value: String): Set<String> =
        json.decodeFromString(listSerializer, value).toSet()
}
