package com.example.mytodoapp.sync

import com.example.mytodoapp.data.*

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import com.example.mytodoapp.utils.PreferenceManager

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
            ?: return Result.success() // Not logged in, skip sync

        val db = TodoDatabase.getDatabase(applicationContext)
        val todoDao = db.todoDao()
        val firestore = FirebaseFirestore.getInstance()
        val prefManager = PreferenceManager(applicationContext)

        return try {
            val pendingGroups = todoDao.getPendingGroups()
            val pendingTasks = todoDao.getPendingTasks()
            val settingsState = prefManager.settingsSyncState.first()

            if (pendingGroups.isEmpty() && pendingTasks.isEmpty() && settingsState != SyncState.PENDING.name) {
                // We purposefully do NOT delete synced soft-deleted groups/tasks here
                // to permanently retain the "Tombstone" locally so 'Delete Wins' works 
                // reliably against delayed edits from other devices.
                return Result.success()
            }

            if (settingsState == SyncState.PENDING.name) {
                val themeMode = prefManager.themeMode.first().name
                val aiRewrite = prefManager.aiRewriteType.first().name
                val pdfConfig = prefManager.pdfConfig.first()
                val moveDone = prefManager.moveDoneToBottom.first()
                val updatedAt = prefManager.settingsUpdatedAt.first()
                val deviceId = prefManager.deviceId.first()

                val settingsMap = mapOf(
                    "themeMode" to themeMode,
                    "aiRewriteType" to aiRewrite,
                    "pdfIncludeStatus" to pdfConfig.includeStatus,
                    "pdfIncludeFavorites" to pdfConfig.includeFavorites,
                    "pdfIncludeSummary" to pdfConfig.includeSummary,
                    "moveDoneToBottom" to moveDone,
                    "updatedAt" to updatedAt,
                    "deviceId" to deviceId
                )

                firestore.collection("users").document(userId)
                    .collection("settings").document("profile")
                    .set(settingsMap, SetOptions.merge())
                    .await()

                prefManager.markSettingsSynced()
            }

            var batch = firestore.batch()
            var opCount = 0
            
            val successfullySyncedGroupIds = mutableListOf<String>()
            val successfullySyncedTaskIds = mutableListOf<String>()

            // Process Groups
            for (group in pendingGroups) {
                val groupRef = firestore.collection("users").document(userId)
                    .collection("groups").document(group.id)
                
                val groupMap = mutableMapOf<String, Any>(
                    "id" to group.id,
                    "title" to group.title,
                    "createdAt" to group.createdAt,
                    "isPinned" to group.isPinned,
                    "updatedAt" to group.updatedAt,
                    "deviceId" to group.deviceId,
                    "syncState" to group.syncState.name
                )
                if (group.deleted) {
                    groupMap["deleted"] = true
                }
                
                batch.set(groupRef, groupMap, SetOptions.merge())
                successfullySyncedGroupIds.add(group.id)
                opCount++

                if (opCount >= 450) {
                    batch.commit().await()
                    todoDao.updateGroupSyncStates(successfullySyncedGroupIds, SyncState.SYNCED)
                    todoDao.updateTaskSyncStates(successfullySyncedTaskIds, SyncState.SYNCED)
                    successfullySyncedGroupIds.clear()
                    successfullySyncedTaskIds.clear()
                    
                    batch = firestore.batch()
                    opCount = 0
                }
            }

            // Process Tasks
            for (task in pendingTasks) {
                val taskRef = firestore.collection("users").document(userId)
                    .collection("groups").document(task.groupId)
                    .collection("tasks").document(task.id)

                val taskMap = mutableMapOf<String, Any>(
                    "id" to task.id,
                    "groupId" to task.groupId,
                    "text" to task.text,
                    "status" to task.status.name,
                    "isFavorite" to task.isFavorite,
                    "position" to task.position,
                    "createdAt" to task.createdAt,
                    "updatedAt" to task.updatedAt,
                    "deviceId" to task.deviceId,
                    "syncState" to task.syncState.name
                )
                if (task.deleted) {
                    taskMap["deleted"] = true
                }

                batch.set(taskRef, taskMap, SetOptions.merge())
                successfullySyncedTaskIds.add(task.id)
                opCount++

                if (opCount >= 450) {
                    batch.commit().await()
                    todoDao.updateGroupSyncStates(successfullySyncedGroupIds, SyncState.SYNCED)
                    todoDao.updateTaskSyncStates(successfullySyncedTaskIds, SyncState.SYNCED)
                    successfullySyncedGroupIds.clear()
                    successfullySyncedTaskIds.clear()
                    
                    batch = firestore.batch()
                    opCount = 0
                }
            }

            if (opCount > 0) {
                batch.commit().await()
                if (successfullySyncedGroupIds.isNotEmpty()) {
                    todoDao.updateGroupSyncStates(successfullySyncedGroupIds, SyncState.SYNCED)
                }
                if (successfullySyncedTaskIds.isNotEmpty()) {
                    todoDao.updateTaskSyncStates(successfullySyncedTaskIds, SyncState.SYNCED)
                }
            }

            // Tombstones are deliberately kept locally forever.

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
