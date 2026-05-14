package com.example.mytodoapp.utils

import android.content.Context
import com.example.mytodoapp.components.RewriteType
import com.example.mytodoapp.data.BackupData
import com.example.mytodoapp.data.TodoDao
import com.example.mytodoapp.data.TodoGroupEntity
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
                    // 1. Try to find an exact ID match first (Highest Accuracy)
                    // 2. If no ID match, fall back to Title match (User intent for same project)
                    val existingLocalGroup = currentLocalGroups.find { it.id == importedGroup.id }
                        ?: currentLocalGroups.find { it.title == importedGroup.title }

                    if (existingLocalGroup != null) {
                        val initialLocalTasks = existingLocalGroup.tasks
                        val localTasksToSave = initialLocalTasks.toMutableList()

                        importedGroup.tasks.forEach { importedTask ->
                            // Check if a task with the same ID or same content already existed locally BEFORE this import
                            val isDuplicate = initialLocalTasks.any { localTask ->
                                localTask.id == importedTask.id || (
                                    localTask.text == importedTask.text &&
                                    localTask.status == importedTask.status &&
                                    localTask.isFavorite == importedTask.isFavorite
                                )
                            }

                            if (isDuplicate) {
                                duplicatesIgnored++
                            } else {
                                localTasksToSave.add(importedTask)
                                tasksImported++
                            }
                        }

                        todoDao.insertGroup(
                            TodoGroupEntity(
                                id = existingLocalGroup.id,
                                title = existingLocalGroup.title,
                                tasks = localTasksToSave,
                                createdAt = existingLocalGroup.createdAt,
                                isPinned = existingLocalGroup.isPinned || importedGroup.isPinned
                            )
                        )

                    } else {
                        todoDao.insertGroup(
                            TodoGroupEntity(
                                id = importedGroup.id,
                                title = importedGroup.title,
                                tasks = importedGroup.tasks,
                                createdAt = importedGroup.createdAt,
                                isPinned = importedGroup.isPinned
                            )
                        )
                        tasksImported += importedGroup.tasks.size
                    }
                }

                backupData.settings.let { settings ->
                    try { preferenceManager.saveThemeMode(ThemeMode.valueOf(settings.themeMode)) } catch (e: Exception) {}
                    try { preferenceManager.saveAiRewriteType(RewriteType.valueOf(settings.aiRewriteType)) } catch (e: Exception) {}
                    preferenceManager.savePdfConfig(com.example.mytodoapp.utils.PdfConfig(
                        includeStatus = settings.pdfIncludeStatus,
                        includeFavorites = settings.pdfIncludeFavorites,
                        includeSummary = settings.pdfIncludeSummary
                    ))
                    preferenceManager.saveMoveDoneToBottom(settings.moveDoneToBottom)
                }

                ImportState.Success(tasksImported, duplicatesIgnored)

            } catch (e: Exception) {
                ImportState.Error(e.message ?: "Failed to import backup")
            }
        }
    }
}
