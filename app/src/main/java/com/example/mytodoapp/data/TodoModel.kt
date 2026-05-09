package com.example.mytodoapp.data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class TodoStatus(val color: Color, val label: String) {
    ComingUp(Color(0xFFEF5350), "Coming Up"),
    Ongoing(Color(0xFF2196F3), "Ongoing"),
    Done(Color(0xFF66BB6A), "Done")
}

@Immutable
data class TodoTask(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val status: TodoStatus = TodoStatus.ComingUp,
    val isFavorite: Boolean = false
)

@Immutable
data class TodoGroup(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val tasks: List<TodoTask> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)