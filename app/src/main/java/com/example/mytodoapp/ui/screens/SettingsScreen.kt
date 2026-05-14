package com.example.mytodoapp.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mytodoapp.R
import com.example.mytodoapp.components.RewriteType
import com.example.mytodoapp.utils.ThemeMode
import com.example.mytodoapp.ui.viewmodel.TodoViewModel
import com.example.mytodoapp.utils.ImportState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import androidx.compose.material.icons.filled.CheckCircle
import androidx.core.content.FileProvider
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    currentAiStyle: RewriteType,
    onAiStyleSelected: (RewriteType) -> Unit,
    currentPdfConfig: com.example.mytodoapp.utils.PdfConfig,
    onPdfConfigChange: (com.example.mytodoapp.utils.PdfConfig) -> Unit,
    moveDoneToBottom: Boolean,
    onMoveDoneToBottomChange: (Boolean) -> Unit,
    viewModel: TodoViewModel,
    onBack: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    
    val lastBackupTime by viewModel.lastBackupTime.collectAsStateWithLifecycle()
    val lastBackupSource by viewModel.lastBackupSource.collectAsStateWithLifecycle()

    var showResetDialog by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }

    var showExportDestinationDialog by remember { mutableStateOf(false) }
    var showImportDestinationDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.let { outputStream ->
                scope.launch {
                    val success = viewModel.exportDatabase(outputStream)
                    if (success) {
                        Toast.makeText(context, "Backup exported successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to export backup", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, "Export cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    val driveExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        Toast.makeText(context, "Backup sent to Drive", Toast.LENGTH_SHORT).show()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.let { inputStream ->
                viewModel.importDatabase(inputStream)
                onNavigateToDashboard()
            }
        }
    }

    val driveImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.openInputStream(uri)?.let { inputStream ->
                    viewModel.importDatabase(inputStream)
                    onNavigateToDashboard()
                }
            }
        }
    }

    fun isDriveInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.google.android.apps.docs", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    if (showExportDestinationDialog) {
        AlertDialog(
            onDismissRequest = { showExportDestinationDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            title = { 
                Text(
                    "Backup Destination", 
                    fontWeight = FontWeight.ExtraBold, 
                    color = MaterialTheme.colorScheme.onSurface 
                ) 
            },
            text = { 
                Text(
                    "Choose where you would like to store your data backup.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showExportDestinationDialog = false
                        val timestamp = SimpleDateFormat("dd-MMM-yyyy_HHmm", Locale.getDefault()).format(Date())
                        exportLauncher.launch("ToDo_Backup_$timestamp.json")
                    },
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Local Device", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showExportDestinationDialog = false
                        scope.launch {
                            try {
                                val zipFile = viewModel.exportDatabaseDrive(context)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    setPackage("com.google.android.apps.docs")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                
                                if (isDriveInstalled()) {
                                    driveExportLauncher.launch(shareIntent)
                                } else {
                                    // Fallback to standard chooser if Drive app is missing
                                    context.startActivity(Intent.createChooser(shareIntent, "Save Backup to Drive"))
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to create Drive backup", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Google Drive", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    if (showImportDestinationDialog) {
        AlertDialog(
            onDismissRequest = { showImportDestinationDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            title = { 
                Text(
                    "Import Source", 
                    fontWeight = FontWeight.ExtraBold, 
                    color = MaterialTheme.colorScheme.onSurface 
                ) 
            },
            text = { 
                Text(
                    "Select the location to restore your data from.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showImportDestinationDialog = false
                        importLauncher.launch(arrayOf("application/json", "application/zip", "application/x-zip-compressed", "*/*"))
                    },
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Local Device", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showImportDestinationDialog = false
                        if (isDriveInstalled()) {
                            val driveIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "application/zip"
                                setPackage("com.google.android.apps.docs")
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_LOCAL_ONLY, false)
                            }
                            driveImportLauncher.launch(driveIntent)
                        } else {
                            // Fallback: Use standard picker which also shows Drive if it's there
                            importLauncher.launch(arrayOf("application/json", "application/zip", "application/x-zip-compressed", "*/*"))
                        }
                    },
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Google Drive", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!isResetting) showResetDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            title = { 
                Text(
                    "Reset App", 
                    fontWeight = FontWeight.ExtraBold, 
                    color = MaterialTheme.colorScheme.onSurface 
                ) 
            },
            text = { 
                if (isResetting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Resetting...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(
                        "Are you sure you want to reset the app? This will delete all projects, tasks, and settings. This action cannot be undone.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                if (!isResetting) {
                    Button(
                        onClick = {
                            isResetting = true
                            viewModel.resetApp {
                                isResetting = false
                                showResetDialog = false
                                onNavigateToDashboard()
                            }
                        }, 
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text("Reset", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!isResetting) {
                    TextButton(
                        onClick = { showResetDialog = false },
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text(
                            "Cancel", 
                            color = MaterialTheme.colorScheme.primary, 
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, // Synchronize with Scaffold background
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            ThemeSection(
                currentTheme = currentTheme,
                onThemeSelected = onThemeSelected
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            TaskOrganizationSection(
                moveDoneToBottom = moveDoneToBottom,
                onMoveDoneToBottomChange = onMoveDoneToBottomChange
            )

            Spacer(modifier = Modifier.height(32.dp))

            AiStyleSection(
                currentAiStyle = currentAiStyle,
                onAiStyleSelected = onAiStyleSelected
            )

            Spacer(modifier = Modifier.height(32.dp))

            PdfConfigSection(
                currentConfig = currentPdfConfig,
                onConfigChange = onPdfConfigChange
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            BackupRestoreSection(
                lastBackupTime = lastBackupTime,
                lastBackupSource = lastBackupSource,
                onExport = { showExportDestinationDialog = true },
                onImport = { showImportDestinationDialog = true }
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            ResetAppSection(
                onResetClick = { showResetDialog = true }
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            AppBrandingFooter()
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ResetAppSection(
    onResetClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Danger Zone",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Text(
            text = "Permanently delete all data and reset preferences.",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onResetClick() }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Reset App",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reset App",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Clear all data and start fresh.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun BackupRestoreSection(
    lastBackupTime: Long,
    lastBackupSource: String?,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Data Safety",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Secure your tasks and preferences by creating a backup.",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                if (lastBackupTime > 0L) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last Backup:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(lastBackupTime))} (${lastBackupSource ?: "Unknown"})",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExport() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Export Backup",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Create Backup",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Save your projects, tasks, and settings locally or to Google Drive.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onImport() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "Import Backup",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Restore Data",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Import your data from a previously saved backup file.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskOrganizationSection(
    moveDoneToBottom: Boolean,
    onMoveDoneToBottomChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Task Organization",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Control how your tasks are displayed.",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMoveDoneToBottomChange(!moveDoneToBottom) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Move done tasks to bottom",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Automatically pushes completed tasks to the end of the list.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }

                Switch(
                    checked = moveDoneToBottom,
                    onCheckedChange = { onMoveDoneToBottomChange(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        checkedIconColor = MaterialTheme.colorScheme.primary,
                        uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
fun PdfConfigSection(
    currentConfig: com.example.mytodoapp.utils.PdfConfig,
    onConfigChange: (com.example.mytodoapp.utils.PdfConfig) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "PDF Generation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Configure details for your exported PDF documents.",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                // SECTION 1: COLUMNS
                Text(
                    text = "Include Columns",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp)
                )

                PdfOptionRow(
                    label = "Status",
                    selected = currentConfig.includeStatus,
                    onClick = { onConfigChange(currentConfig.copy(includeStatus = !currentConfig.includeStatus)) }
                )

                PdfOptionRow(
                    label = "Favorite",
                    selected = currentConfig.includeFavorites,
                    onClick = { onConfigChange(currentConfig.copy(includeFavorites = !currentConfig.includeFavorites)) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                // SECTION 2: ADDITIONAL DETAILS
                Text(
                    text = "Additional Details",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConfigChange(currentConfig.copy(includeSummary = !currentConfig.includeSummary)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Include Summary Table",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Adds counts of statuses and favorites.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }

                    Switch(
                        checked = currentConfig.includeSummary,
                        onCheckedChange = { onConfigChange(currentConfig.copy(includeSummary = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary 
                    else Color.Transparent
                )
                .border(
                    width = 2.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AiStyleSection(
    currentAiStyle: RewriteType,
    onAiStyleSelected: (RewriteType) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "AI Rewrite Style",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Choose the default tone for your task rewrites.",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                RewriteType.entries.forEach { type ->
                    val isSelected = currentAiStyle == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAiStyleSelected(type) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = type.icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = type.settingsDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { onAiStyleSelected(type) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    
                    if (type != RewriteType.entries.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSection(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "App Theme",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Choose your preferred appearance.",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Segmented Control for Theme Options
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val options = listOf(
                ThemeMode.SYSTEM to "System",
                ThemeMode.LIGHT to "Light",
                ThemeMode.DARK to "Dark"
            )
            
            options.forEach { (mode, label) ->
                val isSelected = currentTheme == mode

                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    label = "themeBgColor"
                )

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                    label = "themeTextColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .clickable { onThemeSelected(mode) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AppBrandingFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = "ToDo App",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 12.sp
            )

            Text(
                text = "Version 1.1",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                lineHeight = 8.sp
            )
        }
    }
}
