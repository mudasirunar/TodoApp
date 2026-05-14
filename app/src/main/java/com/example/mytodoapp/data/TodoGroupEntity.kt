package com.example.mytodoapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_groups")
data class TodoGroupEntity(
    @PrimaryKey val id: String,
    val title: String,
    val tasks: List<TodoTask>,
    val createdAt: Long,
    val isPinned: Boolean = false
)
