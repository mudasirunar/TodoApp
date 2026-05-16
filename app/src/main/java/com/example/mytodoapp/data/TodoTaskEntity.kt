package com.example.mytodoapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mytodoapp.sync.SyncState

@Entity(
    tableName = "todo_tasks",
    foreignKeys = [
        ForeignKey(
            entity = TodoGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class TodoTaskEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val text: String,
    val status: TodoStatus,
    var isFavorite: Boolean = false,
    val position: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val syncState: SyncState = SyncState.PENDING,
    val deviceId: String = ""
)
