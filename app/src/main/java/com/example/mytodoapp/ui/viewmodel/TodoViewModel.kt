package com.example.mytodoapp.ui.viewmodel

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mytodoapp.data.TodoDao
import com.example.mytodoapp.data.TodoGroup
import com.example.mytodoapp.data.TodoGroupEntity
import com.example.mytodoapp.components.RewriteType
import com.example.mytodoapp.data.TodoStatus
import com.example.mytodoapp.data.TodoTask
import com.example.mytodoapp.data.BackupData
import com.example.mytodoapp.data.BackupSettings
import com.example.mytodoapp.utils.ThemeMode
import com.example.mytodoapp.utils.PreferenceManager
import com.example.mytodoapp.sync.SyncState
import com.example.mytodoapp.sync.SyncManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.InputStream
import java.io.OutputStream

class RowHistory(initialText: String) {
    private val maxHistory = 30
    val undoStack = ArrayDeque<String>()
    val redoStack = ArrayDeque<String>()
    var currentText: String = initialText

    fun pushState(newText: String) {
        if (newText == currentText) return

        if (currentText.isBlank() && undoStack.isNotEmpty()) {
            currentText = newText
            redoStack.clear()
            return
        }

        undoStack.addLast(currentText)
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        currentText = newText
    }

    fun undo(): String? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(currentText)
        currentText = undoStack.removeLast()
        return currentText
    }

    fun redo(): String? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(currentText)
        currentText = redoStack.removeLast()
        return currentText
    }
}

enum class SortStrategy(val label: String) {
    AlphabeticalAsc("Alphabetical (A-Z)"),
    AlphabeticalDesc("Alphabetical (Z-A)"),
    NewestFirst("Newest First"),
    OldestFirst("Oldest First"),
    FavoritesFirst("Favorites First"),
    Status("By Status")
}

class TodoViewModel(
    private val todoDao: TodoDao,
    private val preferenceManager: PreferenceManager,
    private val syncManager: SyncManager
) : ViewModel() {

    private val backupManager = com.example.mytodoapp.utils.BackupManager(todoDao, preferenceManager)

    private val _importState = MutableStateFlow<com.example.mytodoapp.utils.ImportState>(com.example.mytodoapp.utils.ImportState.Idle)
    val importState: StateFlow<com.example.mytodoapp.utils.ImportState> = _importState.asStateFlow()

    fun resetImportState() {
        _importState.value = com.example.mytodoapp.utils.ImportState.Idle
    }
    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
    
    private suspend fun getBackupData(): BackupData {
        val currentGroups = activeGroups.value
        
        // Fetch actual values from PreferenceManager to ensure they are the latest persisted ones
        val theme = preferenceManager.themeMode.first()
        val aiStyle = preferenceManager.aiRewriteType.first()
        val pdf = preferenceManager.pdfConfig.first()
        val moveDone = preferenceManager.moveDoneToBottom.first()

        val currentSettings = BackupSettings(
            themeMode = theme.name,
            aiRewriteType = aiStyle.name,
            pdfIncludeStatus = pdf.includeStatus,
            pdfIncludeFavorites = pdf.includeFavorites,
            pdfIncludeSummary = pdf.includeSummary,
            moveDoneToBottom = moveDone
        )

        return BackupData(
            version = 1,
            settings = currentSettings,
            groups = currentGroups
        )
    }

    suspend fun exportDatabase(outputStream: OutputStream): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                backupManager.exportToLocal(outputStream, getBackupData())
                saveLastBackupInfo("Local Device")
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun exportDatabaseDrive(context: android.content.Context): java.io.File {
        val file = backupManager.exportToDriveZip(context, getBackupData())
        saveLastBackupInfo("Google Drive")
        return file
    }

    private fun saveLastBackupInfo(source: String) {
        viewModelScope.launch {
            preferenceManager.saveLastBackupInfo(System.currentTimeMillis(), source)
        }
    }

    fun importDatabase(inputStream: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            _importState.value = com.example.mytodoapp.utils.ImportState.Loading
            val result = backupManager.importDatabase(inputStream)
            
            // If import was successful, refresh the local UI states from PreferenceManager
            if (result is com.example.mytodoapp.utils.ImportState.Success) {
                updateStatesFromPrefs()
            }
            
            _importState.value = result
        }
    }

    private suspend fun updateStatesFromPrefs() {
        _themeMode.value = preferenceManager.themeMode.first()
        _aiRewriteType.value = preferenceManager.aiRewriteType.first()
        _pdfConfig.value = preferenceManager.pdfConfig.first()
        _moveDoneToBottom.value = preferenceManager.moveDoneToBottom.first()
    }

    val allGroups: StateFlow<List<TodoGroup>> = combine(
        todoDao.getAllGroups(),
        preferenceManager.moveDoneToBottom
    ) { relations, moveDone ->
        relations.map { relation ->
            // Filter out deleted tasks
            val activeTasks = relation.tasks.filter { !it.deleted }
            
            val sortedTaskEntities = if (moveDone) {
                activeTasks.sortedWith(compareBy({ it.status == TodoStatus.Done }, { it.position }))
            } else {
                activeTasks.sortedBy { it.position }
            }

            TodoGroup(
                id = relation.group.id,
                title = relation.group.title,
                tasks = sortedTaskEntities.map { taskEntity ->
                    TodoTask(
                        id = taskEntity.id,
                        text = taskEntity.text,
                        status = taskEntity.status,
                        isFavorite = taskEntity.isFavorite,
                        position = taskEntity.position,
                        createdAt = taskEntity.createdAt,
                        updatedAt = taskEntity.updatedAt
                    )
                },
                isPinned = relation.group.isPinned,
                createdAt = relation.group.createdAt,
                updatedAt = relation.group.updatedAt,
                isDeleted = relation.group.deleted
            )
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeGroups: StateFlow<List<TodoGroup>> = allGroups
        .map { list -> list.filter { !it.isDeleted } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Settings Logic (Optimistic UI) ---
    private val _themeMode = MutableStateFlow<ThemeMode?>(null)
    val themeMode: StateFlow<ThemeMode?> = _themeMode.asStateFlow()

    private val _aiRewriteType = MutableStateFlow<RewriteType?>(null)
    val aiRewriteType: StateFlow<RewriteType?> = _aiRewriteType.asStateFlow()
    
    private val _pdfConfig = MutableStateFlow<com.example.mytodoapp.utils.PdfConfig?>(null)
    val pdfConfig: StateFlow<com.example.mytodoapp.utils.PdfConfig?> = _pdfConfig.asStateFlow()

    private val _moveDoneToBottom = MutableStateFlow<Boolean?>(null)
    val moveDoneToBottom: StateFlow<Boolean?> = _moveDoneToBottom.asStateFlow()

    val lastBackupTime = preferenceManager.lastBackupTime.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0L
    )
    val lastBackupSource = preferenceManager.lastBackupSource.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    init {
        viewModelScope.launch {
            preferenceManager.themeMode.collect { mode ->
                _themeMode.value = mode
            }
        }
        viewModelScope.launch {
            preferenceManager.aiRewriteType.collect { type ->
                _aiRewriteType.value = type
            }
        }
        viewModelScope.launch {
            preferenceManager.pdfConfig.collect { config ->
                _pdfConfig.value = config
            }
        }
        viewModelScope.launch {
            preferenceManager.moveDoneToBottom.collect { value ->
                _moveDoneToBottom.value = value
            }
        }
    }

    fun saveThemeMode(mode: ThemeMode) {
        _themeMode.value = mode // Instant UI update
        viewModelScope.launch {
            preferenceManager.saveThemeMode(mode)
            syncManager.notifyLocalChange()
        }
    }

    fun saveAiRewriteType(type: RewriteType) {
        _aiRewriteType.value = type // Instant UI update
        viewModelScope.launch {
            preferenceManager.saveAiRewriteType(type)
            syncManager.notifyLocalChange()
        }
    }

    fun savePdfConfig(config: com.example.mytodoapp.utils.PdfConfig) {
        _pdfConfig.value = config // Instant UI update
        viewModelScope.launch {
            preferenceManager.savePdfConfig(config)
            syncManager.notifyLocalChange()
        }
    }

    fun saveMoveDoneToBottom(value: Boolean) {
        _moveDoneToBottom.value = value // Instant UI update
        viewModelScope.launch {
            preferenceManager.saveMoveDoneToBottom(value)
            syncManager.notifyLocalChange()
        }
    }

    fun getSortedTasks(tasks: List<TodoTask>, strategy: SortStrategy, moveDoneToBottom: Boolean): List<TodoTask> {
        var sorted = when (strategy) {
            SortStrategy.AlphabeticalAsc -> tasks.sortedBy { it.text.lowercase() }
            SortStrategy.AlphabeticalDesc -> tasks.sortedByDescending { it.text.lowercase() }
            SortStrategy.NewestFirst -> tasks.sortedByDescending { it.createdAt }
            SortStrategy.OldestFirst -> tasks.sortedBy { it.createdAt }
            SortStrategy.FavoritesFirst -> tasks.sortedByDescending { it.isFavorite }
            SortStrategy.Status -> tasks.sortedBy { it.status.ordinal }
        }

        if (moveDoneToBottom) {
            sorted = sorted.sortedWith(compareBy { it.status == TodoStatus.Done })
        }
        
        return sorted
    }

    // --- Preview System (Non-persistent) ---
    private val _previewGroup = MutableStateFlow<TodoGroup?>(null)
    val previewGroup = _previewGroup.asStateFlow()

    fun setPreviewGroup(group: TodoGroup?) {
        _previewGroup.value = group
    }

    // --- History System ---
    private val rowHistories = mutableMapOf<String, RowHistory>()
    val canUndoMap = mutableStateMapOf<String, Boolean>()
    val canRedoMap = mutableStateMapOf<String, Boolean>()

    fun initHistoryIfNeeds(rowId: String, initialText: String) {
        if (!rowHistories.containsKey(rowId)) {
            rowHistories[rowId] = RowHistory(initialText)
            updateUndoRedoState(rowId)
        }
    }

    fun pushHistory(rowId: String, newText: String) {
        val history = rowHistories[rowId] ?: return
        history.pushState(newText)
        updateUndoRedoState(rowId)
    }

    fun undo(rowId: String): String? {
        val history = rowHistories[rowId] ?: return null
        val res = history.undo()
        updateUndoRedoState(rowId)
        return res
    }

    fun redo(rowId: String): String? {
        val history = rowHistories[rowId] ?: return null
        val res = history.redo()
        updateUndoRedoState(rowId)
        return res
    }

    private fun updateUndoRedoState(rowId: String) {
        val history = rowHistories[rowId] ?: return
        canUndoMap[rowId] = history.undoStack.isNotEmpty()
        canRedoMap[rowId] = history.redoStack.isNotEmpty()
    }

    fun clearHistory() {
        rowHistories.clear()
        canUndoMap.clear()
        canRedoMap.clear()
    }

    // --- Auto-Save System ---
    private var saveJob: kotlinx.coroutines.Job? = null
    private val _currentEditGroup = MutableStateFlow<TodoGroup?>(null)

    fun updateCurrentGroup(group: TodoGroup) {
        if (_currentEditGroup.value == group) return
        _currentEditGroup.value = group
        
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(400) // 400ms debounce
            saveGroupToDb(group)
        }
    }

    fun forceImmediateSave() {
        saveJob?.cancel()
        _currentEditGroup.value?.let { group ->
            viewModelScope.launch(Dispatchers.IO) {
                saveGroupToDb(group)
            }
        }
    }

    private suspend fun saveGroupToDb(group: TodoGroup) {
        // Prevent saving completely empty and newly opened unpinned documents
        if (group.title.isBlank() && group.tasks.size == 1 && group.tasks[0].text.isBlank() && !group.isPinned) return

        val existingGroupEntity = todoDao.getGroupById(group.id)
        if (existingGroupEntity?.deleted == true) {
            // Tombstone active: This project was deleted. Prevent any resurrection via saves.
            return
        }

        val currentTasksInDb = todoDao.getTasksByGroupId(group.id)

        // Determine if group itself changed
        val groupChanged = existingGroupEntity == null || 
                           existingGroupEntity.title != group.title || 
                           existingGroupEntity.isPinned != group.isPinned

        // Determine if tasks changed
        var anyTaskChanged = false
        val currentTaskIds = group.tasks.map { it.id }.toSet()
        
        // Check for deleted tasks
        val tasksDeleted = currentTasksInDb.any { it.id !in currentTaskIds && !it.deleted }
        if (tasksDeleted) anyTaskChanged = true

        val deviceId = preferenceManager.getOrCreateDeviceId()
        val now = System.currentTimeMillis()

        val taskEntities = group.tasks.mapIndexed { index, task ->
            val existingTask = currentTasksInDb.find { it.id == task.id }
            val hasChanged = existingTask == null || 
                             existingTask.text != task.text || 
                             existingTask.status != task.status || 
                             existingTask.isFavorite != task.isFavorite ||
                             existingTask.position != (index + 1) * 100.0
            
            if (hasChanged) anyTaskChanged = true
            
            val taskUpdatedAt = if (hasChanged) now else (existingTask?.updatedAt ?: now)
            val syncState = if (hasChanged) SyncState.PENDING else (existingTask?.syncState ?: SyncState.SYNCED)

            com.example.mytodoapp.data.TodoTaskEntity(
                id = task.id,
                groupId = group.id,
                text = task.text,
                status = task.status,
                isFavorite = task.isFavorite,
                position = (index + 1) * 100.0,
                createdAt = task.createdAt,
                updatedAt = taskUpdatedAt,
                deviceId = deviceId,
                syncState = syncState
            )
        }

        if (!groupChanged && !anyTaskChanged) {
            // Nothing actually changed (only updatedAt might have changed from a merge)
            return
        }

        val groupEntity = TodoGroupEntity(
            id = group.id,
            title = group.title,
            createdAt = group.createdAt,
            isPinned = group.isPinned,
            updatedAt = now,
            deviceId = deviceId,
            syncState = SyncState.PENDING
        )
        
        val groupUpdatedAt = if (groupChanged) now else (existingGroupEntity?.updatedAt ?: now)
        val groupSyncState = if (groupChanged) SyncState.PENDING else (existingGroupEntity?.syncState ?: SyncState.SYNCED)
        
        val finalGroupEntity = groupEntity.copy(
            updatedAt = groupUpdatedAt,
            syncState = groupSyncState
        )

        todoDao.insertGroupWithTasks(finalGroupEntity, taskEntities)
        
        // Instant push for task deletions
        val updatedTaskIds = taskEntities.map { it.id }.toSet()
        currentTasksInDb.filter { it.id !in updatedTaskIds && !it.deleted }.forEach { taskToDelete ->
            viewModelScope.launch {
                syncManager.pushTaskDeletionImmediately(group.id, taskToDelete.id)
            }
        }
        
        // Instant push for updates
        viewModelScope.launch {
            syncManager.pushGroupImmediately(group.copy(updatedAt = now))
        }
        
        syncManager.notifyLocalChange()
    }

    fun deleteGroup(group: TodoGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            todoDao.deleteGroupWithTasks(group.id, SyncState.PENDING)
            // Instant push for group deletion
            viewModelScope.launch {
                syncManager.pushGroupDeletionImmediately(group.id)
            }
            syncManager.notifyLocalChange()
        }
    }

    fun togglePin(group: TodoGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            val deviceId = preferenceManager.getOrCreateDeviceId()
            todoDao.insertGroup(
                TodoGroupEntity(
                    id = group.id,
                    title = group.title,
                    createdAt = group.createdAt,
                    isPinned = !group.isPinned,
                    updatedAt = System.currentTimeMillis(),
                    deviceId = deviceId,
                    syncState = SyncState.PENDING
                )
            )
            // Instant push for pin toggle
            viewModelScope.launch {
                syncManager.pushGroupPinImmediately(group.id, !group.isPinned)
            }
            syncManager.notifyLocalChange()
        }
    }
    fun resetApp(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // Clear Database
            todoDao.deleteAllGroups()
            todoDao.deleteAllTasks()
            
            // Clear Preferences
            preferenceManager.clearAll()
            
            // Reset ViewModel states to defaults
            _themeMode.value = ThemeMode.SYSTEM
            _aiRewriteType.value = RewriteType.Standard
            _pdfConfig.value = com.example.mytodoapp.utils.PdfConfig(
                includeStatus = true,
                includeFavorites = true,
                includeSummary = true
            )
            _moveDoneToBottom.value = false
            
            // Post completion back to main thread
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
}

class TodoViewModelFactory(
    private val todoDao: TodoDao,
    private val preferenceManager: PreferenceManager,
    private val syncManager: com.example.mytodoapp.sync.SyncManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TodoViewModel(todoDao, preferenceManager, syncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
