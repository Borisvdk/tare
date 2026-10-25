package app.tare.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import app.tare.domain.Portion
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Single local database. Schema changes need a version bump plus a migration
 * (schemas are exported to app/schemas for that) — never destructive: this is the user's diary.
 */
@Database(
    entities = [Food::class, LogEntry::class, Meal::class, MealItem::class, RecipeIngredient::class, WeightEntry::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TareDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun logDao(): LogDao
    abstract fun mealDao(): MealDao
    abstract fun recipeDao(): RecipeDao
    abstract fun weightDao(): WeightDao

    companion object {
        const val NAME = "tare.db"

        fun build(context: Context): TareDatabase =
            Room.databaseBuilder(context, TareDatabase::class.java, NAME).build()

        fun inMemory(context: Context): TareDatabase =
            Room.inMemoryDatabaseBuilder(context, TareDatabase::class.java).allowMainThreadQueries().build()
    }
}

// Serializers are looked up inside the function bodies: KSP can't see the
// compiler-plugin-generated Portion.serializer() when it appears in a property type.
class Converters {
    @TypeConverter
    fun portionsToJson(portions: List<Portion>): String =
        json.encodeToString(ListSerializer(Portion.serializer()), portions)

    @TypeConverter
    fun portionsFromJson(text: String): List<Portion> =
        if (text.isBlank()) emptyList() else json.decodeFromString(ListSerializer(Portion.serializer()), text)

    private companion object {
        // Tolerate fields added by newer app versions, so reading data back never crashes.
        val json = Json { ignoreUnknownKeys = true }
    }
}
