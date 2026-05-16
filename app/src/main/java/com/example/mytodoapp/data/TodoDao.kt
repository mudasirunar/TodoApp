package com.example.mytodoapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.mytodoapp.sync.SyncState

@Dao
interface TodoDao {
    @Transaction
    @Query("SELECT * FROM todo_groups ORDER BY isPinned DESC, createdAt DESC")
    fun getAllGroups(): Flow<List<TodoGroupWithTasks>>

    @Query("SELECT * FROM todo_groups WHERE id = :id")
    suspend fun getGroupById(id: String): TodoGroupEntity?

    @Query("SELECT * FROM todo_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): TodoTaskEntity?

    @Query("SELECT * FROM todo_tasks WHERE groupId = :groupId")
    suspend fun getTasksByGroupId(groupId: String): List<TodoTaskEntity>

    @Upsert
    suspend fun insertGroup(group: TodoGroupEntity)

    @Upsert
    suspend fun insertTasks(tasks: List<TodoTaskEntity>)

    @Transaction
    suspend fun insertGroupWithTasks(group: TodoGroupEntity, tasks: List<TodoTaskEntity>) {
        insertGroup(group)
        
        // 1. Identify tasks to be deleted (were in DB for this group but not in new list)
        val currentTaskIds = tasks.map { it.id }.toSet()
        val existingTasks = getTasksByGroupId(group.id)
        val now = System.currentTimeMillis()
        
        existingTasks.forEach { existing ->
            if (existing.id !in currentTaskIds && !existing.deleted) {
                softDeleteTask(existing.id, SyncState.PENDING, now)
            }
        }
        
        // 2. Insert/Update the new task list
        insertTasks(tasks)
    }

    @Query("UPDATE todo_groups SET deleted = 1, syncState = :syncState, updatedAt = :updatedAt WHERE id = :groupId")
    suspend fun softDeleteGroup(groupId: String, syncState: SyncState, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE todo_tasks SET deleted = 1, syncState = :syncState, updatedAt = :updatedAt WHERE groupId = :groupId")
    suspend fun softDeleteTasksByGroupId(groupId: String, syncState: SyncState, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE todo_tasks SET deleted = 1, syncState = :syncState, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun softDeleteTask(taskId: String, syncState: SyncState, updatedAt: Long = System.currentTimeMillis())

    @Transaction
    suspend fun deleteGroupWithTasks(groupId: String, syncState: SyncState) {
        val now = System.currentTimeMillis()
        softDeleteGroup(groupId, syncState, now)
        softDeleteTasksByGroupId(groupId, syncState, now)
    }

    @Query("DELETE FROM todo_groups")
    suspend fun deleteAllGroups()

    @Query("DELETE FROM todo_tasks")
    suspend fun deleteAllTasks()

    // --- Sync Engine Queries ---
    
    @Query("SELECT * FROM todo_groups WHERE syncState = 'PENDING'")
    suspend fun getPendingGroups(): List<TodoGroupEntity>

    @Query("SELECT * FROM todo_tasks WHERE syncState = 'PENDING'")
    suspend fun getPendingTasks(): List<TodoTaskEntity>

    @Query("UPDATE todo_groups SET syncState = :syncState WHERE id IN (:ids)")
    suspend fun updateGroupSyncStates(ids: List<String>, syncState: SyncState)

    @Query("UPDATE todo_tasks SET syncState = :syncState WHERE id IN (:ids)")
    suspend fun updateTaskSyncStates(ids: List<String>, syncState: SyncState)

    @Query("DELETE FROM todo_groups WHERE deleted = 1 AND syncState = 'SYNCED'")
    suspend fun deleteSyncedSoftDeletedGroups()

    @Query("DELETE FROM todo_tasks WHERE deleted = 1 AND syncState = 'SYNCED'")
    suspend fun deleteSyncedSoftDeletedTasks()

    // --- Migration Queries ---
    
    @Query("UPDATE todo_groups SET syncState = 'PENDING'")
    suspend fun markAllGroupsPending()

    @Query("UPDATE todo_tasks SET syncState = 'PENDING'")
    suspend fun markAllTasksPending()
}
