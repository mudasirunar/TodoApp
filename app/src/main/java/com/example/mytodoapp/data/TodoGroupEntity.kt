package com.example.mytodoapp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.mytodoapp.sync.SyncState
import com.google.firebase.firestore.PropertyName

@Entity(tableName = "todo_groups")
data class TodoGroupEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    @ColumnInfo(name = "isPinned") @get:PropertyName("isPinned") @set:PropertyName("isPinned") var isPinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val syncState: SyncState = SyncState.PENDING,
    val deviceId: String = ""
)
