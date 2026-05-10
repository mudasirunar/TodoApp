package com.example.mytodoapp.data

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.util.UUID

@Parcelize
enum class TodoStatus(val color: @RawValue Color, val label: String) : Parcelable {
    ComingUp(Color(0xFFEF5350), "Coming Up"),
    Ongoing(Color(0xFF2196F3), "Ongoing"),
    Done(Color(0xFF66BB6A), "Done")
}

@Immutable
@Parcelize
data class TodoTask(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val status: TodoStatus = TodoStatus.ComingUp,
    val isFavorite: Boolean = false
) : Parcelable

@Immutable
@Parcelize
data class TodoGroup(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val tasks: List<TodoTask> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
) : Parcelable