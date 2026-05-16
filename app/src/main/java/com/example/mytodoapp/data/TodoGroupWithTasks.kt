package com.example.mytodoapp.data

import androidx.room.Embedded
import androidx.room.Relation

data class TodoGroupWithTasks(
    @Embedded val group: TodoGroupEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val tasks: List<TodoTaskEntity>
)
