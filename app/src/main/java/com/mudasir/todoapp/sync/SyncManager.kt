package com.mudasir.todoapp.sync

import com.mudasir.todoapp.data.*

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
import com.mudasir.todoapp.components.RewriteType
import com.mudasir.todoapp.utils.PdfConfig
import com.mudasir.todoapp.utils.PreferenceManager
import com.mudasir.todoapp.utils.ThemeMode

class SyncManager(
    private val context: Context,
    private val prefManager: PreferenceManager,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _isSyncing = MutableStateFlow(FirebaseAuth.getInstance().currentUser != null)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // SharedFlow to act as an event bus for local changes
    private val syncTrigger = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _initialSettingsReceived = MutableStateFlow(false)

    private var initialGroupsLoaded = false
    private val pendingTaskInitialLoads = java.util.concurrent.atomic.AtomicInteger(0)

    // True if the initial Firebase snapshot contained active (non-deleted) groups
    private val _initialSyncHadData = MutableStateFlow(false)
    val initialSyncHadData: StateFlow<Boolean> = _initialSyncHadData.asStateFlow()

    private fun checkInitialSyncComplete() {
        if (initialGroupsLoaded && pendingTaskInitialLoads.get() <= 0) {
            _isSyncing.value = false
        }
    }

    suspend fun waitForInitialSettings() {
        _initialSettingsReceived.first { it }
    }

    init {
        // Start the debouncer
        @OptIn(FlowPreview::class)
        externalScope.launch {
            syncTrigger
                .debounce(3000L)
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

        // Reset for the current user session
        _initialSettingsReceived.value = false

        // Stop any existing listeners
        stopRealtimeSync()

        _isSyncing.value = true // Start syncing state for initial fetch

        settingsListener = firestore.collection("users").document(userId)
            .collection("settings").document("profile")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    _initialSettingsReceived.value = true // Don't block on error
                    return@addSnapshotListener
                }

                externalScope.launch {
                    val forceRemote = prefManager.forceRemoteSettings.first()

                    if (!snapshot.exists()) {
                        // New account: If we just signed in from guest, upload local settings
                        if (forceRemote) {
                            prefManager.setForceRemoteSettings(false)
                            prefManager.markSettingsPending()
                            notifyLocalChange()
                        }
                        _initialSettingsReceived.value = true
                        return@launch
                    }

                    try {
                        // Existing account: Always apply remote settings on first login/sync
                        val remoteUpdatedAt = snapshot.getLong("updatedAt") ?: 0L
                        val localUpdatedAt = prefManager.settingsUpdatedAt.first()

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
                        }
                        _initialSettingsReceived.value = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _initialSettingsReceived.value = true
                    }
                }
            }

        initialGroupsLoaded = false
        pendingTaskInitialLoads.set(0)
        _initialSyncHadData.value = false

        groupListener = firestore.collection("users").document(userId).collection("groups")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    if (!initialGroupsLoaded) {
                        initialGroupsLoaded = true
                        checkInitialSyncComplete()
                    }
                    return@addSnapshotListener
                }

                externalScope.launch {
                    val localDeviceId = prefManager.deviceId.first()

                    val isInitialCallback = !initialGroupsLoaded
                    if (isInitialCallback) {
                        // Count active groups being added to determine how many task snapshot listeners we need to track
                        val activeGroupsToFetch = snapshot.documentChanges.filter {
                            it.type == DocumentChange.Type.ADDED && !(it.document.getBoolean("deleted") ?: false)
                        }
                        pendingTaskInitialLoads.set(activeGroupsToFetch.size)
                        _initialSyncHadData.value = activeGroupsToFetch.isNotEmpty()
                    }

                    for (docChange in snapshot.documentChanges) {
                        try {
                            val doc = docChange.document
                            val deviceId = doc.getString("deviceId") ?: ""

                            // Prevent echo loop for REMOVED items, but for others LWW handles it safely.
                            val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                            val id = doc.getString("id") ?: doc.id

                            val localGroup = todoDao.getGroupById(id)
                            val localUpdatedAt = localGroup?.updatedAt ?: 0L
                            val isLocalDeleted = localGroup?.deleted ?: false
                            val isRemoteDeleted = doc.getBoolean("deleted") ?: false

                            // If locally deleted and remote is trying to make active, delete wins unless remote is strictly newer (deliberate reactivation)
                            if (isLocalDeleted && !isRemoteDeleted && remoteUpdatedAt <= localUpdatedAt) {
                                continue
                            }

                            if (remoteUpdatedAt > localUpdatedAt) {
                                val groupEntity = TodoGroupEntity(
                                    id = id,
                                    title = doc.getString("title") ?: "",
                                    createdAt = doc.getLong("createdAt") ?: 0L,
                                    isPinned = doc.getBoolean("isPinned") ?: doc.getBoolean("pinned") ?: false,
                                    updatedAt = remoteUpdatedAt,
                                    deleted = isRemoteDeleted,
                                    syncState = SyncState.SYNCED,
                                    deviceId = deviceId
                                )
                                todoDao.insertGroup(groupEntity)
                            }

                            listenToTasksForGroup(userId, id, todoDao, localDeviceId) {
                                if (isInitialCallback) {
                                    val remaining = pendingTaskInitialLoads.decrementAndGet()
                                    if (remaining <= 0) {
                                        checkInitialSyncComplete()
                                    }
                                }
                            }

                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (isInitialCallback) {
                        initialGroupsLoaded = true
                        if (pendingTaskInitialLoads.get() == 0) {
                            checkInitialSyncComplete()
                        }
                    }
                }
            }
    }

    private fun listenToTasksForGroup(
        userId: String,
        groupId: String,
        todoDao: TodoDao,
        localDeviceId: String,
        onInitialLoaded: () -> Unit = {}
    ) {
        if (taskListeners.containsKey(groupId)) {
            onInitialLoaded()
            return
        }
        val firestore = FirebaseFirestore.getInstance()
        var hasFiredInitial = false
        val listener = firestore.collection("users").document(userId)
            .collection("groups").document(groupId)
            .collection("tasks")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    if (!hasFiredInitial) {
                        hasFiredInitial = true
                        onInitialLoaded()
                    }
                    return@addSnapshotListener
                }
                externalScope.launch {
                    for (docChange in snapshot.documentChanges) {
                        try {
                            val doc = docChange.document
                            val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                            val taskId = doc.getString("id") ?: doc.id
                            val localTask = todoDao.getTaskById(taskId)
                            val localUpdatedAt = localTask?.updatedAt ?: 0L
                            val isLocalDeleted = localTask?.deleted ?: false
                            val isRemoteDeleted = doc.getBoolean("deleted") ?: false

                            // If locally deleted and remote is active, delete wins unless remote is strictly newer
                            if (isLocalDeleted && !isRemoteDeleted && remoteUpdatedAt <= localUpdatedAt) {
                                continue
                            }

                            if (remoteUpdatedAt > localUpdatedAt) {
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
                                    updatedAt = remoteUpdatedAt,
                                    deleted = isRemoteDeleted,
                                    syncState = SyncState.SYNCED,
                                    deviceId = doc.getString("deviceId") ?: ""
                                )
                                todoDao.insertTasks(listOf(taskEntity))
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    if (!hasFiredInitial) {
                        hasFiredInitial = true
                        onInitialLoaded()
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
        _isSyncing.value = false
    }

    suspend fun pushSettingsImmediately() {
        val userId = auth.currentUser?.uid ?: return
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
        try {
            firestore.collection("users").document(userId)
                .collection("settings").document("profile")
                .set(settingsMap, SetOptions.merge())
                .await()
            prefManager.markSettingsSynced()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun pushChangesImmediately(group: TodoGroupEntity?, tasks: List<TodoTaskEntity>) {
        val userId = auth.currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()
        val batch = firestore.batch()

        group?.let {
            val groupRef = firestore.collection("users").document(userId)
                .collection("groups").document(it.id)
            val groupMap = mapOf(
                "id" to it.id,
                "title" to it.title,
                "createdAt" to it.createdAt,
                "isPinned" to it.isPinned,
                "updatedAt" to it.updatedAt,
                "deleted" to it.deleted,
                "deviceId" to it.deviceId
            )
            batch.set(groupRef, groupMap, SetOptions.merge())
        }

        tasks.forEach { task ->
            val taskRef = firestore.collection("users").document(userId)
                .collection("groups").document(task.groupId)
                .collection("tasks").document(task.id)
            val taskMap = mapOf(
                "id" to task.id,
                "groupId" to task.groupId,
                "text" to task.text,
                "status" to task.status.name,
                "isFavorite" to task.isFavorite,
                "position" to task.position,
                "createdAt" to task.createdAt,
                "updatedAt" to task.updatedAt,
                "deleted" to task.deleted,
                "deviceId" to task.deviceId
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
        val deviceId = prefManager.deviceId.first()
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
        val deviceId = prefManager.deviceId.first()
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
        val deviceId = prefManager.deviceId.first()
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

    suspend fun migrateLocalDataToCloud(includeSettings: Boolean = true) {
        val todoDao = TodoDatabase.getDatabase(context).todoDao()
        todoDao.markAllGroupsPending()
        todoDao.markAllTasksPending()

        if (includeSettings) {
            prefManager.markSettingsPending()
        }

        notifyLocalChange()
    }

    fun resetSyncState() {
        _isSyncing.value = false
        _initialSettingsReceived.value = true
        initialGroupsLoaded = true
        pendingTaskInitialLoads.set(0)
        _initialSyncHadData.value = false
    }
}