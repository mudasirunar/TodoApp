package com.example.mytodoapp.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TodoGroupEntity::class, TodoTaskEntity::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todo_groups ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create new todo_tasks table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `todo_tasks` (
                        `id` TEXT NOT NULL, 
                        `groupId` TEXT NOT NULL, 
                        `text` TEXT NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `isFavorite` INTEGER NOT NULL, 
                        `position` REAL NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `deleted` INTEGER NOT NULL, 
                        `syncState` TEXT NOT NULL, 
                        `deviceId` TEXT NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`groupId`) REFERENCES `todo_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_tasks_groupId` ON `todo_tasks` (`groupId`)")

                // 2. Add new columns to todo_groups (Note: We removed 'position' from here to avoid adding it unnecessarily)
                db.execSQL("ALTER TABLE todo_groups ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
                db.execSQL("ALTER TABLE todo_groups ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE todo_groups ADD COLUMN syncState TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE todo_groups ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")

                // 3. Migrate data from todo_groups.tasks (JSON) to todo_tasks
                val cursor = db.query("SELECT id, tasks FROM todo_groups")
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<com.example.mytodoapp.data.TodoTask>>() {}.type
                
                val now = System.currentTimeMillis()
                if (cursor.moveToFirst()) {
                    do {
                        val groupId = cursor.getString(0)
                        val tasksJson = cursor.getString(1)
                        if (!tasksJson.isNullOrEmpty()) {
                            val tasks: List<com.example.mytodoapp.data.TodoTask> = gson.fromJson(tasksJson, type)
                            tasks.forEachIndexed { index, task ->
                                db.execSQL("""
                                    INSERT INTO todo_tasks (id, groupId, text, status, isFavorite, position, createdAt, updatedAt, deleted, syncState, deviceId)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 'PENDING', '')
                                """, arrayOf(task.id, groupId, task.text, task.status.name, if (task.isFavorite) 1 else 0, (index + 1) * 100.0, task.createdAt, now))
                            }
                        }
                    } while (cursor.moveToNext())
                }
                cursor.close()

                // 4. Create new table without tasks column, copy, and replace
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `todo_groups_new` (
                        `id` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `isPinned` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `deleted` INTEGER NOT NULL, 
                        `syncState` TEXT NOT NULL, 
                        `deviceId` TEXT NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                
                db.execSQL("""
                    INSERT INTO todo_groups_new (id, title, createdAt, isPinned, updatedAt, deleted, syncState, deviceId)
                    SELECT id, title, createdAt, isPinned, updatedAt, deleted, syncState, deviceId FROM todo_groups
                """.trimIndent())
                
                db.execSQL("DROP TABLE todo_groups")
                db.execSQL("ALTER TABLE todo_groups_new RENAME TO todo_groups")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // DROP 'position' column from todo_groups
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `todo_groups_new` (
                        `id` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `isPinned` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `deleted` INTEGER NOT NULL, 
                        `syncState` TEXT NOT NULL, 
                        `deviceId` TEXT NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO todo_groups_new (id, title, createdAt, isPinned, updatedAt, deleted, syncState, deviceId)
                    SELECT id, title, createdAt, isPinned, updatedAt, deleted, syncState, deviceId FROM todo_groups
                """.trimIndent())

                db.execSQL("DROP TABLE todo_groups")
                db.execSQL("ALTER TABLE todo_groups_new RENAME TO todo_groups")
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}