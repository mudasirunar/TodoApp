package com.example.mytodoapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_groups")
    fun getAllGroups(): Flow<List<TodoGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: TodoGroupEntity)

    @Delete
    suspend fun deleteGroup(group: TodoGroupEntity)

    @Query("DELETE FROM todo_groups")
    suspend fun deleteAllGroups()
}
