package com.example.mytodoapp.data

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class TodoStatus(val color: Color, val label: String) {
    ComingUp(Color(0xFFEF5350), "Coming Up"),
    Ongoing(Color(0xFF2196F3), "Ongoing"),
    Done(Color(0xFF66BB6A), "Done")
}

data class TodoTask(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var status: TodoStatus = TodoStatus.ComingUp,
    var isFavorite: Boolean = false
)

data class TodoGroup(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val tasks: List<TodoTask> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)