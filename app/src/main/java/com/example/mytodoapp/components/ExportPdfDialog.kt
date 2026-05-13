package com.example.mytodoapp.components

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.mytodoapp.data.TodoTask
import com.example.mytodoapp.utils.PdfConfig
import com.example.mytodoapp.utils.PdfHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportPdfDialog(
    title: String,
    tasks: List<TodoTask>,
    config: PdfConfig = PdfConfig(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var exportTempPdfFile by remember { mutableStateOf<File?>(null) }
    var isExportGenerating by remember { mutableStateOf(true) }
    var showLoadingUi by remember { mutableStateOf(false) }
    var isActionsDisabled by remember { mutableStateOf(true) }
    var localConfig by remember { mutableStateOf(config) }

    LaunchedEffect(isExportGenerating) {
        if (isExportGenerating) {
            delay(250) // Wait before showing the loader to avoid jitter for fast generations
            showLoadingUi = true
            isActionsDisabled = true
        } else {
            showLoadingUi = false
            isActionsDisabled = false
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                PdfHelper.generateTodoPdf(context, uri, title, tasks, localConfig)
                Toast.makeText(context, "PDF saved successfully!", Toast.LENGTH_SHORT).show()
                onDismiss()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(localConfig) {
        isExportGenerating = true
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = context.cacheDir
                val uniqueFolder = File(cacheDir, "export_${System.currentTimeMillis()}")
                if (!uniqueFolder.exists()) uniqueFolder.mkdirs()

                val projectName = title.ifBlank { "Untitled" }.replace(Regex("[^a-zA-Z0-9]"), "_")
                val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                val fileName = "${projectName}_TodoList_$dateStr.pdf"
                val file = File(uniqueFolder, fileName)

                FileOutputStream(file).use {
                    PdfHelper.writePdfToStream(it, title, tasks, localConfig)
                }

                withContext(Dispatchers.Main) {
                    exportTempPdfFile = file
                    isExportGenerating = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isExportGenerating = false
                    Toast.makeText(context, "Error generating export", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Export as PDF",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (showLoadingUi) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Generating PDF...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(
                        "Choose how you want to export your Todo list.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        "Include Columns:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { localConfig = localConfig.copy(includeStatus = !localConfig.includeStatus) }
                        ) {
                            Checkbox(
                                checked = localConfig.includeStatus,
                                onCheckedChange = { localConfig = localConfig.copy(includeStatus = it) }
                            )
                            Text("Status", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { localConfig = localConfig.copy(includeFavorites = !localConfig.includeFavorites) }
                        ) {
                            Checkbox(
                                checked = localConfig.includeFavorites,
                                onCheckedChange = { localConfig = localConfig.copy(includeFavorites = it) }
                            )
                            Text("Favorites", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // DOWNLOAD BUTTON (WIDER)
                        Button(
                            onClick = {
                                val projectName = title.ifBlank { "Untitled" }.replace(Regex("[^a-zA-Z0-9]"), "_")
                                val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                                val fileName = "${projectName}_TodoList_$dateStr.pdf"
                                createDocumentLauncher.launch(fileName)
                            },
                            enabled = !isActionsDisabled,
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Download", maxLines = 1)
                        }

                        Spacer(Modifier.width(12.dp))

                        // SHARE BUTTON
                        OutlinedButton(
                            onClick = {
                                exportTempPdfFile?.let { file ->
                                    if (file.exists()) {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share PDF"))
                                    }
                                }
                                onDismiss()
                            },
                            enabled = !isActionsDisabled && exportTempPdfFile?.exists() == true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}