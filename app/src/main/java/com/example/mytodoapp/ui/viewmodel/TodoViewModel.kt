package com.example.mytodoapp.ui.viewmodel

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mytodoapp.data.TodoDao
import com.example.mytodoapp.data.TodoGroup
import com.example.mytodoapp.data.TodoGroupEntity
import com.example.mytodoapp.components.RewriteType
import com.example.mytodoapp.utils.ThemeMode
import com.example.mytodoapp.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

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

class TodoViewModel(
    private val todoDao: TodoDao,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    // StateFlow holding the list of TodoGroups
    val groups: StateFlow<List<TodoGroup>?> = todoDao.getAllGroups()
        .map { entities ->
            entities.map { entity ->
                TodoGroup(
                    id = entity.id,
                    title = entity.title,
                    tasks = entity.tasks,
                    createdAt = entity.createdAt,
                    isPinned = entity.isPinned
                )
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // --- Settings Logic (Optimistic UI) ---
    private val _themeMode = MutableStateFlow<ThemeMode?>(null)
    val themeMode: StateFlow<ThemeMode?> = _themeMode.asStateFlow()

    private val _aiRewriteType = MutableStateFlow<RewriteType?>(null)
    val aiRewriteType: StateFlow<RewriteType?> = _aiRewriteType.asStateFlow()
    
    private val _pdfConfig = MutableStateFlow<com.example.mytodoapp.utils.PdfConfig?>(null)
    val pdfConfig: StateFlow<com.example.mytodoapp.utils.PdfConfig?> = _pdfConfig.asStateFlow()

    init {
        viewModelScope.launch {
            preferenceManager.themeMode.collect { mode ->
                if (_themeMode.value == null) {
                    _themeMode.value = mode
                }
            }
        }
        viewModelScope.launch {
            preferenceManager.aiRewriteType.collect { type ->
                if (_aiRewriteType.value == null) {
                    _aiRewriteType.value = type
                }
            }
        }
        viewModelScope.launch {
            preferenceManager.pdfConfig.collect { config ->
                if (_pdfConfig.value == null) {
                    _pdfConfig.value = config
                }
            }
        }
    }

    fun saveThemeMode(mode: ThemeMode) {
        _themeMode.value = mode // Instant UI update
        viewModelScope.launch {
            preferenceManager.saveThemeMode(mode)
        }
    }

    fun saveAiRewriteType(type: RewriteType) {
        _aiRewriteType.value = type // Instant UI update
        viewModelScope.launch {
            preferenceManager.saveAiRewriteType(type)
        }
    }

    fun savePdfConfig(config: com.example.mytodoapp.utils.PdfConfig) {
        _pdfConfig.value = config // Instant UI update
        viewModelScope.launch {
            preferenceManager.savePdfConfig(config)
        }
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

    fun insertGroup(group: TodoGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            todoDao.insertGroup(
                TodoGroupEntity(
                    id = group.id,
                    title = group.title,
                    tasks = group.tasks,
                    createdAt = group.createdAt,
                    isPinned = group.isPinned
                )
            )
        }
    }

    fun deleteGroup(group: TodoGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            todoDao.deleteGroup(
                TodoGroupEntity(
                    id = group.id,
                    title = group.title,
                    tasks = group.tasks,
                    createdAt = group.createdAt,
                    isPinned = group.isPinned
                )
            )
        }
    }

    fun togglePin(group: TodoGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            todoDao.insertGroup(
                TodoGroupEntity(
                    id = group.id,
                    title = group.title,
                    tasks = group.tasks,
                    createdAt = group.createdAt,
                    isPinned = !group.isPinned
                )
            )
        }
    }
}

class TodoViewModelFactory(
    private val todoDao: TodoDao,
    private val preferenceManager: PreferenceManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TodoViewModel(todoDao, preferenceManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
