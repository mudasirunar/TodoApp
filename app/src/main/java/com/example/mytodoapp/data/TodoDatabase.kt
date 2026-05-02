package com.example.mytodoapp.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

// This class tells Room how to handle the List<TodoTask>
class Converters {
    @TypeConverter
    fun fromTaskList(value: List<TodoTask>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toTaskList(value: String): List<TodoTask> {
        val listType = object : TypeToken<List<TodoTask>>() {}.type
        return Gson().fromJson(value, listType)
    }
}

@Entity(tableName = "todo_groups")
data class TodoGroupEntity(
    @PrimaryKey val id: String,
    val title: String,
    val tasks: List<TodoTask>,
    val createdAt: Long,
    val isPinned: Boolean = false
)

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_groups")
    fun getAllGroups(): Flow<List<TodoGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: TodoGroupEntity)

    @Delete
    suspend fun deleteGroup(group: TodoGroupEntity)
}

// 1. Change version from 1 to 2
@Database(entities = [TodoGroupEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        // 2. Define the migration logic
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todo_groups ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: TodoDatabase? = null

        fun getDatabase(context: Context): TodoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TodoDatabase::class.java,
                    "todo_database"
                )
                    .addMigrations(MIGRATION_1_2) // 3. Add the migration here
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}