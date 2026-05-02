
package com.example.mytodoapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mytodoapp.data.TodoDao
import com.example.mytodoapp.data.TodoGroup
import com.example.mytodoapp.data.TodoGroupEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodoViewModel(private val todoDao: TodoDao) : ViewModel() {

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
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

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

class TodoViewModelFactory(private val todoDao: TodoDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TodoViewModel(todoDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
