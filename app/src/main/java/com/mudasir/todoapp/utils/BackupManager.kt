package com.mudasir.todoapp.utils

import android.content.Context
import com.mudasir.todoapp.components.RewriteType
import com.mudasir.todoapp.data.BackupData
import com.mudasir.todoapp.data.TodoDao
import com.mudasir.todoapp.data.TodoGroupEntity
import com.mudasir.todoapp.sync.SyncState
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    data class Success(val tasksImported: Int, val duplicatesIgnored: Int) : ImportState()
    data class Error(val message: String) : ImportState()
}

class BackupManager(
    private val todoDao: TodoDao,
    private val preferenceManager: PreferenceManager
) {

    suspend fun exportToLocal(outputStream: OutputStream, backupData: BackupData) {
        withContext(Dispatchers.IO) {
            val gson = Gson()
            val jsonString = gson.toJson(backupData)
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(jsonString)
            }
        }
    }

    suspend fun exportToDriveZip(context: Context, backupData: BackupData): File {
        return withContext(Dispatchers.IO) {
            val gson = Gson()
            val jsonString = gson.toJson(backupData)
            val timestamp = SimpleDateFormat("dd-MMM-yyyy_HHmm", Locale.getDefault()).format(Date())
            val zipFile = File(context.cacheDir, "ToDo_Backup_$timestamp.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val entry = ZipEntry("backup.json")
                zos.putNextEntry(entry)
                zos.write(jsonString.toByteArray())
                zos.closeEntry()
            }
            zipFile
        }
    }

    suspend fun importDatabase(inputStream: InputStream): ImportState {
        return withContext(Dispatchers.IO) {
            try {
                val contentBytes = inputStream.readBytes()
                if (contentBytes.isEmpty()) return@withContext ImportState.Error("Selected file is empty.")

                val jsonString = try {
                    val zis = ZipInputStream(contentBytes.inputStream())
                    val entry = zis.nextEntry
                    if (entry != null && entry.name.endsWith(".json")) {
                        val reader = InputStreamReader(zis)
                        reader.readText()
                    } else {
                        String(contentBytes)
                    }
                } catch (e: Exception) {
                    // Fallback to string if ZIP handling fails (might be a plain JSON file)
                    String(contentBytes)
                }

                if (jsonString.isBlank()) return@withContext ImportState.Error("No valid data found in the file.")

                val gson = Gson()
                val backupData = try {
                    gson.fromJson(jsonString, BackupData::class.java)
                } catch (e: Exception) {
                    null
                }

                if (backupData == null || backupData.groups == null) {
                    return@withContext ImportState.Error("The selected file is not a valid ToDo App backup.")
                }

                var tasksImported = 0
                var duplicatesIgnored = 0


                val currentLocalGroups = todoDao.getAllGroups().first()

                backupData.groups.forEach { importedGroup ->
                    val existingLocalGroup = currentLocalGroups.find { it.group.id == importedGroup.id }

                    val now = System.currentTimeMillis()
                    val deviceId = preferenceManager.getOrCreateDeviceId()

                    if (existingLocalGroup != null) {
                        val localTasksMap = existingLocalGroup.tasks.associateBy { it.id }.toMutableMap()

                        importedGroup.tasks.forEach { importedTask ->
                            val existingLocalTask = localTasksMap[importedTask.id]

                            if (existingLocalTask == null) {
                                val newTask = com.mudasir.todoapp.data.TodoTaskEntity(
                                    id = importedTask.id,
                                    groupId = existingLocalGroup.group.id,
                                    text = importedTask.text,
                                    status = importedTask.status,
                                    isFavorite = importedTask.isFavorite,
                                    position = importedTask.position,
                                    createdAt = importedTask.createdAt,
                                    updatedAt = now,
                                    deleted = false,
                                    syncState = SyncState.PENDING,
                                    deviceId = deviceId
                                )
                                localTasksMap[importedTask.id] = newTask
                                tasksImported++
                            } else {
                                if (existingLocalTask.deleted) {
                                    val updatedTask = existingLocalTask.copy(
                                        text = importedTask.text,
                                        status = importedTask.status,
                                        isFavorite = importedTask.isFavorite,
                                        position = importedTask.position,
                                        updatedAt = now,
                                        deleted = false,
                                        syncState = SyncState.PENDING,
                                        deviceId = deviceId
                                    )
                                    localTasksMap[importedTask.id] = updatedTask
                                    tasksImported++
                                } else {
                                    duplicatesIgnored++
                                }
                            }
                        }

                        val groupEntity = existingLocalGroup.group.copy(
                            title = importedGroup.title,
                            isPinned = existingLocalGroup.group.isPinned || importedGroup.isPinned,
                            updatedAt = now,
                            deleted = false,
                            syncState = SyncState.PENDING,
                            deviceId = deviceId
                        )

                        todoDao.insertGroupWithTasks(groupEntity, localTasksMap.values.toList())

                    } else {
                        val groupEntity = TodoGroupEntity(
                            id = importedGroup.id,
                            title = importedGroup.title,
                            createdAt = importedGroup.createdAt,
                            isPinned = importedGroup.isPinned,
                            updatedAt = now,
                            deleted = false,
                            syncState = SyncState.PENDING,
                            deviceId = deviceId
                        )

                        val taskEntities = importedGroup.tasks.map { task ->
                            com.mudasir.todoapp.data.TodoTaskEntity(
                                id = task.id,
                                groupId = groupEntity.id,
                                text = task.text,
                                status = task.status,
                                isFavorite = task.isFavorite,
                                position = task.position,
                                createdAt = task.createdAt,
                                updatedAt = now,
                                deleted = false,
                                syncState = SyncState.PENDING,
                                deviceId = deviceId
                            )
                        }

                        todoDao.insertGroupWithTasks(groupEntity, taskEntities)
                        tasksImported += importedGroup.tasks.size
                    }
                }

                backupData.settings.let { settings ->
                    try { preferenceManager.saveThemeMode(ThemeMode.valueOf(settings.themeMode)) } catch (e: Exception) {}
                    try { preferenceManager.saveAiRewriteType(RewriteType.valueOf(settings.aiRewriteType)) } catch (e: Exception) {}
                    preferenceManager.savePdfConfig(com.mudasir.todoapp.utils.PdfConfig(
                        includeStatus = settings.pdfIncludeStatus,
                        includeFavorites = settings.pdfIncludeFavorites,
                        includeSummary = settings.pdfIncludeSummary
                    ))
                    preferenceManager.saveMoveDoneToBottom(settings.moveDoneToBottom)
                }

                preferenceManager.resetMigrationState()

                ImportState.Success(tasksImported, duplicatesIgnored)

            } catch (e: Exception) {
                ImportState.Error(e.message ?: "Failed to import backup")
            }
        }
    }
}
