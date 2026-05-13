package com.example.mytodoapp.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.mytodoapp.data.TodoGroup
import com.example.mytodoapp.utils.PdfHelper
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    group: TodoGroup,
    config: com.example.mytodoapp.utils.PdfConfig = com.example.mytodoapp.utils.PdfConfig(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var tempPdfFile by remember { mutableStateOf<File?>(null) }
    var isGenerating by remember { mutableStateOf(true) }
    var showLoadingUi by remember { mutableStateOf(false) }
    var isActionsDisabled by remember { mutableStateOf(true) }
    var localConfig by remember { mutableStateOf(config) }

    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            kotlinx.coroutines.delay(250)
            showLoadingUi = true
            isActionsDisabled = true
        } else {
            showLoadingUi = false
            isActionsDisabled = false
        }
    }
    
    // PDF Save Logic (Final Export)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                PdfHelper.generateTodoPdf(context, uri, group.title, group.tasks, localConfig)
                Toast.makeText(context, "PDF saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ ASYNCHRONOUS GENERATION: Move I/O off the main thread to prevent UI freezing
    LaunchedEffect(group, localConfig) {
        isGenerating = true
        withContext(Dispatchers.IO) {
            try {
                // 1. Create a unique subfolder
                val uniqueFolder = File(context.cacheDir, "preview_${System.currentTimeMillis()}")
                if (!uniqueFolder.exists()) uniqueFolder.mkdirs()
                
                // 2. Clean up old preview folders
                context.cacheDir.listFiles { file -> 
                    file.isDirectory && file.name.startsWith("preview_") && file != uniqueFolder
                }?.forEach { it.deleteRecursively() }

                // 3. Generate the file with professional name: ProjectName_TodoList_YYYY-MM-DD.pdf
                val projectName = group.title.ifBlank { "Untitled" }.replace(Regex("[^a-zA-Z0-9]"), "_")
                val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                val fileName = "${projectName}_TodoList_$dateStr.pdf"
                val file = File(uniqueFolder, fileName)

                FileOutputStream(file).use {
                    PdfHelper.writePdfToStream(it, group.title, group.tasks, localConfig)
                }
                
                // 4. Update state on main thread
                withContext(Dispatchers.Main) {
                    tempPdfFile = file
                    isGenerating = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isGenerating = false
                    Toast.makeText(context, "Error generating preview", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Share Logic
    val onSharePdf = {
        val file = tempPdfFile
        if (file != null && file.exists()) {
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
        } else {
            Toast.makeText(context, "Preview not ready", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preview", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Include:", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(end = 12.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { localConfig = localConfig.copy(includeStatus = !localConfig.includeStatus) }
                        ) {
                            Checkbox(
                                checked = localConfig.includeStatus,
                                onCheckedChange = { localConfig = localConfig.copy(includeStatus = it) },
                                modifier = Modifier.size(32.dp)
                            )
                            Text("Status", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { localConfig = localConfig.copy(includeFavorites = !localConfig.includeFavorites) }
                        ) {
                            Checkbox(
                                checked = localConfig.includeFavorites,
                                onCheckedChange = { localConfig = localConfig.copy(includeFavorites = it) },
                                modifier = Modifier.size(32.dp)
                            )
                            Text("Favorites", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val projectName = group.title.ifBlank { "Untitled" }.replace(Regex("[^a-zA-Z0-9]"), "_")
                                val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                                val fileName = "${projectName}_TodoList_$dateStr.pdf"
                                createDocumentLauncher.launch(fileName)
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            enabled = !isActionsDisabled
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onSharePdf,
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            enabled = !isActionsDisabled
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Share", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFFE2E8F0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            // ✅ CROSSFADE FOR SMOOTH TRANSITION BETWEEN FILES
            androidx.compose.animation.Crossfade(
                targetState = tempPdfFile,
                label = "pdfFade",
                animationSpec = androidx.compose.animation.core.tween(500)
            ) { file ->
                if (file != null && file.exists()) {
                    AndroidView(
                        factory = { ctx ->
                            PDFView(ctx, null).apply {
                                setBackgroundColor(android.graphics.Color.parseColor("#BDC3C7"))
                                fromFile(file)
                                    .enableSwipe(true)
                                    .swipeHorizontal(false)
                                    .enableDoubletap(true)
                                    .defaultPage(0)
                                    .enableAnnotationRendering(false)
                                    .password(null)
                                    .scrollHandle(DefaultScrollHandle(ctx))
                                    .enableAntialiasing(true)
                                    .spacing(25)
                                    .fitEachPage(true)
                                    .pageFitPolicy(FitPolicy.WIDTH)
                                    .load()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // ✅ SHOW LOADER AS AN OVERLAY (Prevents screen from going empty/white)
            if (showLoadingUi) {
                Surface(
                    color = Color.Black.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Generating Preview...", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
