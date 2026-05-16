package com.example.mytodoapp.sync

import com.example.mytodoapp.data.*

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import com.example.mytodoapp.components.RewriteType
import com.example.mytodoapp.utils.PdfConfig
import com.example.mytodoapp.utils.PreferenceManager
import com.example.mytodoapp.utils.ThemeMode

class SyncManager(
    private val context: Context,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // SharedFlow to act as an event bus for local changes
    private val syncTrigger = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _initialSettingsReceived = MutableSharedFlow<Unit>(replay = 1)
    
    suspend fun waitForInitialSettings() {
        _initialSettingsReceived.first()
    }

    init {
        // Start the debouncer
        @OptIn(FlowPreview::class)
        externalScope.launch {
            syncTrigger
                .debounce(3000L) // Wait 3 seconds after the last change before triggering sync
                .collect {
                    enqueueBatchSync()
                }
        }
    }

    /**
     * Call this whenever a local edit happens
     */
    fun notifyLocalChange() {
        syncTrigger.tryEmit(Unit)
    }

    private fun enqueueBatchSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "BatchSyncWorker",
            ExistingWorkPolicy.REPLACE, // If already running/queued, replace it
            syncRequest
        )
    }

    private val taskListeners = mutableMapOf<String, ListenerRegistration>()
    private var groupListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null

    fun startRealtimeSync() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()
        val todoDao = TodoDatabase.getDatabase(context).todoDao()
        val prefManager = PreferenceManager(context)

        // Stop any existing listeners
        stopRealtimeSync()
        
        _isSyncing.value = true // Start syncing state for initial fetch

        settingsListener = firestore.collection("users").document(userId)
            .collection("settings").document("profile")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                
                externalScope.launch {
                    if (!snapshot.exists()) {
                        _initialSettingsReceived.emit(Unit)
                        return@launch
                    }
                    try {
                        val localDeviceId = prefManager.deviceId.first()
                        val deviceId = snapshot.getString("deviceId") ?: ""
                        
                        val remoteUpdatedAt = snapshot.getLong("updatedAt") ?: 0L
                        val localUpdatedAt = prefManager.settingsUpdatedAt.first()
                        val forceRemote = prefManager.forceRemoteSettings.first()
                        
                        if (forceRemote || remoteUpdatedAt > localUpdatedAt) {
                             if (forceRemote) {
                                 prefManager.setForceRemoteSettings(false)
                             }
                            val themeModeStr = snapshot.getString("themeMode")
                            val themeMode = themeModeStr?.let { try { ThemeMode.valueOf(it) } catch(e:Exception) { null } }
                            
                            val aiRewriteStr = snapshot.getString("aiRewriteType")
                            val aiRewriteType = aiRewriteStr?.let { try { RewriteType.valueOf(it) } catch(e:Exception) { null } }
                            
                            val pdfConfig = PdfConfig(
                                includeStatus = snapshot.getBoolean("pdfIncludeStatus") ?: true,
                                includeFavorites = snapshot.getBoolean("pdfIncludeFavorites") ?: true,
                                includeSummary = snapshot.getBoolean("pdfIncludeSummary") ?: true
                            )
                            
                            val moveDoneToBottom = snapshot.getBoolean("moveDoneToBottom")
                            
                            prefManager.applyRemoteSettings(
                                themeMode = themeMode,
                                aiRewriteType = aiRewriteType,
                                pdfConfig = pdfConfig,
                                moveDoneToBottom = moveDoneToBottom,
                                updatedAt = remoteUpdatedAt
                            )
                             _initialSettingsReceived.emit(Unit)
                        } else {
                            // If remote is not newer, still mark as received if it's our first time
                            _initialSettingsReceived.emit(Unit)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _initialSettingsReceived.emit(Unit) // Ensure we don't block login on error
                    }
                }
            }

        groupListener = firestore.collection("users").document(userId).collection("groups")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                externalScope.launch {
                    _isSyncing.value = true
                    val localDeviceId = PreferenceManager(context).deviceId.first()

                    for (docChange in snapshot.documentChanges) {
                        try {
                            val doc = docChange.document
                            val deviceId = doc.getString("deviceId") ?: ""
                            
                            // Prevent echo loop for REMOVED items, but for others LWW handles it safely.
                            val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                            val id = doc.getString("id") ?: doc.id
                            
                            val localGroup = todoDao.getGroupById(id)
                            val localUpdatedAt = localGroup?.updatedAt ?: 0L
                            val isRemoteDeleted = doc.getBoolean("deleted") ?: false

                            if (isRemoteDeleted || remoteUpdatedAt > localUpdatedAt) {
                                val groupEntity = TodoGroupEntity(
                                    id = id,
                                    title = doc.getString("title") ?: "",
                                    createdAt = doc.getLong("createdAt") ?: 0L,
                                    isPinned = doc.getBoolean("isPinned") ?: doc.getBoolean("pinned") ?: false,
                                    updatedAt = if (isRemoteDeleted) maxOf(remoteUpdatedAt, localUpdatedAt + 1) else remoteUpdatedAt,
                                    deleted = isRemoteDeleted,
                                    syncState = SyncState.SYNCED,
                                    deviceId = deviceId
                                )
                                todoDao.insertGroup(groupEntity)
                            }
                            
                            listenToTasksForGroup(userId, id, todoDao, localDeviceId)
                            
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    _isSyncing.value = false // Initial fetch of groups is done
                }
            }
    }

    private fun listenToTasksForGroup(userId: String, groupId: String, todoDao: TodoDao, localDeviceId: String) {
        if (taskListeners.containsKey(groupId)) return
        val firestore = FirebaseFirestore.getInstance()
        val listener = firestore.collection("users").document(userId)
            .collection("groups").document(groupId)
            .collection("tasks")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                externalScope.launch {
                    for (docChange in snapshot.documentChanges) {
                        try {
                            val doc = docChange.document
                            val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                            val taskId = doc.getString("id") ?: doc.id
                            val localTask = todoDao.getTaskById(taskId)
                            val localUpdatedAt = localTask?.updatedAt ?: 0L
                            val isRemoteDeleted = doc.getBoolean("deleted") ?: false

                            if (isRemoteDeleted || remoteUpdatedAt > localUpdatedAt) {
                                val statusStr = doc.getString("status") ?: TodoStatus.ComingUp.name
                                val status = try { TodoStatus.valueOf(statusStr) } catch(e:Exception) { TodoStatus.ComingUp }
                                
                                val taskEntity = TodoTaskEntity(
                                    id = taskId,
                                    groupId = groupId,
                                    text = doc.getString("text") ?: "",
                                    status = status,
                                    isFavorite = doc.getBoolean("isFavorite") ?: doc.getBoolean("favorite") ?: false,
                                    position = doc.getDouble("position") ?: 0.0,
                                    createdAt = doc.getLong("createdAt") ?: 0L,
                                    updatedAt = if (isRemoteDeleted) maxOf(remoteUpdatedAt, localUpdatedAt + 1) else remoteUpdatedAt,
                                    deleted = isRemoteDeleted,
                                    syncState = SyncState.SYNCED,
                                    deviceId = doc.getString("deviceId") ?: ""
                                )
                                todoDao.insertTasks(listOf(taskEntity))
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            }
        taskListeners[groupId] = listener
    }

    fun stopRealtimeSync() {
        settingsListener?.remove()
        settingsListener = null
        groupListener?.remove()
        groupListener = null
        taskListeners.values.forEach { it.remove() }
        taskListeners.clear()
    }

    suspend fun pushGroupImmediately(group: TodoGroup) {
        val userId = auth.currentUser?.uid ?: return
        val deviceId = PreferenceManager(context).deviceId.first()
        val now = System.currentTimeMillis()
        
        val groupMap = mapOf(
            "id" to group.id,
            "title" to group.title,
            "createdAt" to group.createdAt,
            "isPinned" to group.isPinned,
            "updatedAt" to now,
            "deviceId" to deviceId
        )

        val firestore = FirebaseFirestore.getInstance()
        val batch = firestore.batch()

        val groupRef = firestore.collection("users").document(userId)
            .collection("groups").document(group.id)

        batch.set(groupRef, groupMap, SetOptions.merge())

        group.tasks.forEachIndexed { index, task ->
            val taskRef = groupRef.collection("tasks").document(task.id)
            val taskMap = mapOf(
                "id" to task.id,
                "groupId" to group.id,
                "text" to task.text,
                "status" to task.status.name,
                "isFavorite" to task.isFavorite,
                "position" to (index + 1) * 100.0,
                "createdAt" to task.createdAt,
                "updatedAt" to now,
                "deviceId" to deviceId
            )
            batch.set(taskRef, taskMap, SetOptions.merge())
        }
        
        try {
            batch.commit().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun pushTaskDeletionImmediately(groupId: String, taskId: String) {
        val userId = auth.currentUser?.uid ?: return
        val deviceId = PreferenceManager(context).deviceId.first()
        val now = System.currentTimeMillis()
        
        val taskRef = firestore.collection("users").document(userId)
            .collection("groups").document(groupId)
            .collection("tasks").document(taskId)
            
        val deletionMap = mapOf(
            "deleted" to true,
            "updatedAt" to now,
            "deviceId" to deviceId
        )
        
        try {
            taskRef.set(deletionMap, SetOptions.merge()).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun pushGroupDeletionImmediately(groupId: String) {
        val userId = auth.currentUser?.uid ?: return
        val deviceId = PreferenceManager(context).deviceId.first()
        val now = System.currentTimeMillis()
        
        val groupRef = firestore.collection("users").document(userId)
            .collection("groups").document(groupId)
            
        val deletionMap = mapOf(
            "deleted" to true,
            "updatedAt" to now,
            "deviceId" to deviceId
        )
        
        try {
            groupRef.set(deletionMap, SetOptions.merge()).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun pushGroupPinImmediately(groupId: String, isPinned: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val deviceId = PreferenceManager(context).deviceId.first()
        val now = System.currentTimeMillis()
        
        val groupRef = firestore.collection("users").document(userId)
            .collection("groups").document(groupId)
            
        val pinMap = mapOf(
            "isPinned" to isPinned,
            "updatedAt" to now,
            "deviceId" to deviceId
        )
        
        try {
            groupRef.set(pinMap, SetOptions.merge()).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun migrateLocalDataToCloud() {
        val todoDao = TodoDatabase.getDatabase(context).todoDao()
        todoDao.markAllGroupsPending()
        todoDao.markAllTasksPending()
        
        val prefManager = PreferenceManager(context)
        prefManager.markSettingsPending()

        notifyLocalChange()
    }
}
