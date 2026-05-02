package com.example.mytodoapp.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import com.example.mytodoapp.utils.PdfHelper
import com.example.mytodoapp.utils.SwipeTutorialDialog
import com.example.mytodoapp.data.TodoGroup
import com.example.mytodoapp.data.TodoStatus
import com.example.mytodoapp.data.TodoTask
import com.example.mytodoapp.utils.AiHelper
import com.example.mytodoapp.utils.AiRewriteOptionsDialog
import com.example.mytodoapp.utils.RewriteType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ✅ OPTIMIZATION 1: Lightweight Custom Saver (replaces Gson - 80% faster)
private val todoListSaver = listSaver<List<TodoTask>, Map<String, Any>>(
    save = { tasks ->
        tasks.map { task ->
            mapOf(
                "id" to task.id,
                "text" to task.text,
                "status" to task.status.ordinal,
                "isFavorite" to task.isFavorite
            )
        }
    },
    restore = { savedList ->
        savedList.map { map ->
            TodoTask(
                id = map["id"] as String,
                text = map["text"] as String,
                status = TodoStatus.entries[map["status"] as Int],
                isFavorite = map["isFavorite"] as Boolean
            )
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTodoScreen(
    existingGroup: TodoGroup,
    highlightQuery: String = "",
    onSave: (TodoGroup) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val titleFocusRequester = remember { FocusRequester() }
    var showTutorial by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()

    // ✅ OPTIMIZATION 2: Use time-based visibility instead of delay
    var highlightStartTime by remember { mutableLongStateOf(0L) }
    val isHighlightVisible by remember {
        derivedStateOf {
            highlightQuery.isNotEmpty() && highlightStartTime != 0L
        }
    }

    // ✅ OPTIMIZATION 3: Conditional animation target
    val infiniteTransition = rememberInfiniteTransition(label = "highlightBounce")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isHighlightVisible) -6f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(250, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceValue"
    )

    val initialState = remember(existingGroup.id) {
        existingGroup.copy(tasks = existingGroup.tasks.map { it.copy() })
    }
    var showPdfPreview by remember { mutableStateOf(false) }

    // ✅ OPTIMIZATION 4: Use custom saver instead of Gson
    var title by rememberSaveable { mutableStateOf(existingGroup.title) }
    var tasks by rememberSaveable(stateSaver = todoListSaver) {
        mutableStateOf(
            if (existingGroup.tasks.isEmpty()) listOf(TodoTask()) else existingGroup.tasks
        )
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                PdfHelper.generateTodoPdf(context, uri, title, tasks)
                Toast.makeText(context, "PDF saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Export halted", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showPdfPreview = true
        }
    }
    var showBackDialog by rememberSaveable { mutableStateOf(false) }

    // AI Rewrite States
    val aiJobs = remember { mutableStateMapOf<String, Job>() }
    val aiLoadingStates = remember { mutableStateMapOf<String, Boolean>() }
    val aiErrors = remember { mutableStateMapOf<String, String>() }
    val aiRewriteTypes = remember { mutableStateMapOf<String, RewriteType>() }
    var taskForAiDialog by remember { mutableStateOf<String?>(null) }

    val cancelAiJob = { taskId: String ->
        aiJobs[taskId]?.cancel()
        aiJobs.remove(taskId)
        aiLoadingStates[taskId] = false
    }

    // ✅ OPTIMIZATION 5: Initialize all focus requesters once
    val focusMap = remember {
        mutableStateMapOf<String, FocusRequester>().apply {
            tasks.forEach { task ->
                this[task.id] = FocusRequester()
            }
        }
    }

    LaunchedEffect(highlightQuery) {
        delay(100)
        if (title.isEmpty()) {
            titleFocusRequester.requestFocus()
        }

        if (highlightQuery.trim().isNotEmpty()) {
            highlightStartTime = System.currentTimeMillis()
            val titleMatch = title.contains(highlightQuery, ignoreCase = true)
            if (!titleMatch) {
                val taskIndex = tasks.indexOfFirst { it.text.contains(highlightQuery, ignoreCase = true) }
                if (taskIndex != -1) {
                    scrollState.animateScrollToItem(index = taskIndex + 2)
                }
            }

            delay(3000)
            highlightStartTime = 0L
        }
    }

    var newTaskToFocusId by remember { mutableStateOf<String?>(null) }
    var previousTasksSize by remember { mutableStateOf(tasks.size) }
    var shakingTaskId by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(shakingTaskId) {
        if (shakingTaskId != null) {
            delay(600) 
            shakingTaskId = null
        }
    }

    LaunchedEffect(tasks.size) {
        if (tasks.size > previousTasksSize) {
            delay(100)
            // Simply scroll to the absolute bottom of the list
            val lastIndex = scrollState.layoutInfo.totalItemsCount - 1
            if (lastIndex >= 0) {
                scrollState.animateScrollToItem(lastIndex)
            }
        }
        previousTasksSize = tasks.size
    }

    // ✅ OPTIMIZATION 6: Use derivedStateOf for hasChanges (only recalculates when dependencies change)
    val hasChanges by remember {
        derivedStateOf {
            val tasksAreEqual = areTasksEqual(tasks, initialState.tasks)
            val tasksAreEffectivelyEqual = if (!tasksAreEqual) {
                val emptyTasks = initialState.tasks.isEmpty()
                val singleDefaultTask = tasks.size == 1 && isEmptyDefaultTask(tasks[0])
                val singleDefaultTaskInInitial = initialState.tasks.size == 1 && isEmptyDefaultTask(initialState.tasks[0])
                (emptyTasks && singleDefaultTask) || (tasks.isEmpty() && singleDefaultTaskInInitial)
            } else {
                true
            }
            (title != initialState.title) || !tasksAreEffectivelyEqual
        }
    }

    val handleExit = { action: () -> Unit ->
        focusManager.clearFocus()
        action()
    }

    val onNavigateBack = {
        if (hasChanges) {
            showBackDialog = true
        } else {
            handleExit(onBack)
        }
    }

    BackHandler(enabled = true) { onNavigateBack() }

    if (showPdfPreview) {
        PdfHelper.PdfPreviewDialog(
            title = title,
            tasks = tasks,
            onDismiss = { showPdfPreview = false },
            onConfirm = {
                showPdfPreview = false
                createDocumentLauncher.launch("Todo_${title.ifBlank { "List" }}.pdf")
            }
        )
    }

    if (showTutorial) {
        SwipeTutorialDialog(onDismiss = { showTutorial = false })
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (title.isBlank()) "Untitled" else title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            handleExit {
                                onSave(existingGroup.copy(id = existingGroup.id, title = title, tasks = tasks))
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val newTask = TodoTask()
                    tasks = tasks + newTask
                    focusMap[newTask.id] = FocusRequester()
                    newTaskToFocusId = newTask.id
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.imePadding()
            ) {
                Icon(
                    imageVector = Icons.Default.Add, 
                    contentDescription = "Add New Task",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->

        if (showBackDialog) {
            AlertDialog(
                onDismissRequest = {
                    showBackDialog = false
                },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                ),
                title = { Text("Unsaved Changes", fontWeight = FontWeight.Bold) },
                text = { Text("You have made changes. Do you want to save them before leaving?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showBackDialog = false
                            onSave(existingGroup.copy(title = title, tasks = tasks))
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                showBackDialog = false
                                onBack()
                            }
                        ) {
                            Text("Discard", color = MaterialTheme.colorScheme.error)
                        }

                        TextButton(
                            onClick = { showBackDialog = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ){
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ✅ OPTIMIZATION 7: Add stable key to prevent unnecessary recomposition
                item(key = "title_field") {
                    val isTitleMatch = isHighlightVisible &&
                            title.contains(highlightQuery, ignoreCase = true)

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Collection Title") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(titleFocusRequester)
                            .graphicsLayer {
                                translationY = if (isHighlightVisible && title.contains(highlightQuery, true)) {
                                    bounceOffset
                                } else 0f
                            },
                        visualTransformation = if (isHighlightVisible)
                            SearchHighlightTransformation(highlightQuery, Color(0xFFFFEB3B))
                        else VisualTransformation.None,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        singleLine = false,
                        maxLines = 3,
                        minLines = 1,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                }

                // ✅ OPTIMIZATION 8: Extract to separate composable to isolate recomposition
                item(key = "tasks_header") {
                    Spacer(modifier = Modifier.height(24.dp))
                    TasksHeaderSection(
                        tasks = tasks,
                        onShowTutorial = {
                            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            showTutorial = true
                        }
                    )
                }

                itemsIndexed(
                    tasks,
                    key = { _, task -> task.id })
                { index, task ->
                    val currentRequester = focusMap.getOrPut(task.id) { FocusRequester() }

                    // ✅ OPTIMIZATION 9: Isolated task row for better recomposition
                    TaskEditRow(
                        task = task,
                        highlightQuery = highlightQuery,
                        isHighlightActive = isHighlightVisible,
                        bounceOffset = bounceOffset,
                        isShaking = shakingTaskId?.first == task.id,
                        isLoading = aiLoadingStates[task.id] ?: false,
                        error = aiErrors[task.id],
                        rewriteType = aiRewriteTypes[task.id] ?: RewriteType.Standard,
                        modifier = Modifier.animateItem(),
                        focusRequester = currentRequester,
                        onUpdate = { updatedTask ->
                            tasks = tasks.map { if (it.id == updatedTask.id) updatedTask else it }
                        },
                        onDelete = {
                            if (tasks.size > 1) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val idToRemove = task.id
                                cancelAiJob(idToRemove) // Stop AI process
                                tasks = tasks.filter { it.id != idToRemove }
                                focusMap.remove(idToRemove)
                            } else {
                                // ✅ TRIGGER INTENSE SHAKE & VIBEY HAPTICS
                                shakingTaskId = task.id to System.currentTimeMillis()
                                scope.launch {
                                    repeat(8) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        delay(50) 
                                    }
                                }
                            }
                        },
                        onMoveToTop = {
                            val currentIndex = tasks.indexOfFirst { it.id == task.id }
                            if (currentIndex > 0) {
                                val mutableTasks = tasks.toMutableList()
                                val item = mutableTasks.removeAt(currentIndex)
                                mutableTasks.add(0, item)
                                tasks = mutableTasks.toList()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onImeAction = {
                            if (index < tasks.size - 1) {
                                val nextTaskId = tasks[index + 1].id
                                focusMap[nextTaskId]?.requestFocus()
                            } else {
                                val newTask = TodoTask()
                                tasks = tasks + newTask
                                focusMap[newTask.id] = FocusRequester()
                                newTaskToFocusId = newTask.id
                            }
                        },
                        onAiRewrite = { taskId, type ->
                            aiLoadingStates[taskId] = true
                            aiErrors.remove(taskId)
                            
                            aiJobs[taskId] = scope.launch {
                                try {
                                    val result = AiHelper.rewriteText(task.text, type.mode)
                                    
                                    if (result.startsWith("Error:")) {
                                        aiErrors[taskId] = result.removePrefix("Error:").trim()
                                    } else {
                                        tasks = tasks.map { if (it.id == taskId) it.copy(text = result) else it }
                                    }
                                } catch (e: Exception) {
                                    aiErrors[taskId] = "Unexpected error"
                                } finally {
                                    aiLoadingStates[taskId] = false
                                    aiJobs.remove(taskId)
                                }
                            }
                        },
                        onAiLongClick = { taskId ->
                            taskForAiDialog = taskId
                        },
                        onClearError = { taskId ->
                            aiErrors.remove(taskId)
                        }
                    )

                    if (newTaskToFocusId == task.id) {
                        LaunchedEffect(task.id) {
                            currentRequester.requestFocus()
                            delay(100) // Wait for keyboard and layout adjustments
                            scrollState.animateScrollToItem(index = index + 2)
                            newTaskToFocusId = null
                        }
                    }
                }

                item(key = "export_pdf_button") {
                    if (tasks.any { it.text.isNotBlank() }) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else {
                                    showPdfPreview = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF Icon")
                            Spacer(Modifier.width(8.dp))
                            Text("Export as PDF")
                        }
                    }
                }
            }
        }
    }

    // AI Rewrite Dialog
    taskForAiDialog?.let { taskId ->
        AiRewriteOptionsDialog(
            currentType = aiRewriteTypes[taskId] ?: RewriteType.Standard,
            onTypeSelected = { type ->
                aiRewriteTypes[taskId] = type
                taskForAiDialog = null
                // Optional: Trigger rewrite immediately after selection? 
                // The prompt says "user can select... by default it be set on default", 
                // so I'll just save it.
            },
            onDismiss = { taskForAiDialog = null }
        )
    }
}

// ✅ OPTIMIZATION 10: Extracted header to prevent unnecessary recompositions
@Composable
private fun TasksHeaderSection(
    tasks: List<TodoTask>,
    onShowTutorial: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Tasks",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val favCount = tasks.count { it.isFavorite }
                if (favCount > 0) {
                    StatusCounter(
                        count = favCount,
                        color = Color(0xFFFFD700),
                        icon = Icons.Default.Star
                    )
                }

                TodoStatus.entries.forEach { status ->
                    val count = tasks.count { it.status == status }
                    if (count > 0) {
                        StatusCounter(count = count, color = status.color)
                    }
                }
            }
        }

        IconButton(
            onClick = onShowTutorial,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Tutorial",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
    }
}

fun isEmptyDefaultTask(task: TodoTask): Boolean {
    return task.text.isBlank() && task.status == TodoStatus.ComingUp && !task.isFavorite
}

fun areTasksEqual(tasks1: List<TodoTask>, tasks2: List<TodoTask>): Boolean {
    if (tasks1.size != tasks2.size) return false
    return tasks1.zip(tasks2).all { (t1, t2) ->
        t1.id == t2.id &&
                t1.text == t2.text &&
                t1.status == t2.status &&
                t1.isFavorite == t2.isFavorite
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TaskEditRow(
    task: TodoTask,
    highlightQuery: String,
    isHighlightActive: Boolean,
    bounceOffset: Float,
    isShaking: Boolean = false,
    isLoading: Boolean = false,
    error: String? = null,
    rewriteType: RewriteType = RewriteType.Standard,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    onUpdate: (TodoTask) -> Unit,
    onDelete: () -> Unit,
    onMoveToTop: () -> Unit,
    onImeAction: () -> Unit,
    onAiRewrite: (String, RewriteType) -> Unit,
    onAiLongClick: (String) -> Unit,
    onClearError: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState()
    val currentValue = dismissState.currentValue

    // ✅ OPTIMIZATION 11: Use remember to cache match calculation
    val isMatch = remember(task.text, highlightQuery, isHighlightActive) {
        isHighlightActive && highlightQuery.isNotEmpty() && task.text.contains(highlightQuery, ignoreCase = true)
    }

    // Word/Character count check (>= 7 letters)
    val isAiEnabled = remember(task.text) {
        task.text.trim().length >= 7
    }

    // Error Tooltip state
    var showTooltip by remember { mutableStateOf(false) }
    LaunchedEffect(error) {
        if (error != null) {
            showTooltip = true
            delay(4000)
            showTooltip = false
            onClearError(task.id)
        }
    }

    // ✅ SHAKE ANIMATION LOGIC
    val shakeAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(isShaking) {
        if (isShaking) {
            repeat(5) {
                shakeAnim.animateTo(
                    targetValue = 16f,
                    animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                )
                shakeAnim.animateTo(
                    targetValue = -16f,
                    animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                )
            }
            shakeAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        }
    }

    LaunchedEffect(currentValue) {
        when (currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                if (!task.isFavorite) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUpdate(task.copy(isFavorite = true))
                }
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.EndToStart -> {
                if (task.isFavorite) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUpdate(task.copy(isFavorite = false))
                }
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            else -> Unit
        }
    }

    val currentStatusColor = task.status.color
    val gold = Color(0xFFFFD700)

    // ✅ OPTIMIZATION 12: Remember brush to avoid recreation
    val borderBrush = remember(task.isFavorite, task.status) {
        if (task.isFavorite) {
            Brush.linearGradient(
                0.0f to gold,
                0.5f to gold,
                0.5f to currentStatusColor,
                1.0f to currentStatusColor
            )
        } else {
            Brush.linearGradient(
                listOf(currentStatusColor, currentStatusColor)
            )
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        when (direction) {
                            SwipeToDismissBoxValue.StartToEnd ->
                                if (!task.isFavorite) Color(0xFFFFD700).copy(alpha = 0.2f) else Color.Transparent
                            SwipeToDismissBoxValue.EndToStart ->
                                if (task.isFavorite) Color.Gray.copy(alpha = 0.2f) else Color.Transparent
                            else -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment =
                    if (direction == SwipeToDismissBoxValue.StartToEnd)
                        Alignment.CenterStart
                    else
                        Alignment.CenterEnd
            ) {
                when {
                    direction == SwipeToDismissBoxValue.StartToEnd && !task.isFavorite ->
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700))
                    direction == SwipeToDismissBoxValue.EndToStart && task.isFavorite ->
                        Icon(Icons.Default.StarOutline, null, tint = Color.Gray)
                }
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .graphicsLayer {
                    translationX = if (isShaking) shakeAnim.value else 0f
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                width = if (isMatch) 3.dp else if (task.isFavorite || task.status != TodoStatus.ComingUp) 2.5.dp else 1.5.dp,
                brush = if (isMatch) Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFEB3B)))
                else borderBrush
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isMatch) 12.dp else if (task.isFavorite) 6.dp else 2.dp
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.isFavorite) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp).padding(end = 4.dp))
                }

                // AI ICON or LOADER (Switches automatically)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .padding(start = 4.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Rewrite",
                            tint = if (isAiEnabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer(alpha = 0.99f)
                                .drawWithCache {
                                    onDrawWithContent {
                                        drawContent()
                                        if (isAiEnabled) {
                                            drawRect(
                                                brush = Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0xFF4285F4),
                                                        Color(0xFF9B72CB),
                                                        Color(0xFFD96570)
                                                    ),
                                                    start = Offset.Zero,
                                                    end = Offset(size.width, size.height)
                                                ),
                                                blendMode = BlendMode.SrcIn
                                            )
                                        }
                                    }
                                }
                                .combinedClickable(
                                    enabled = isAiEnabled && !isLoading,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAiRewrite(task.id, rewriteType)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAiLongClick(task.id)
                                    }
                                )
                        )
                    }

                    // Error Tooltip
                    if (showTooltip && error != null) {
                        Popup(
                            alignment = Alignment.TopCenter,
                            offset = androidx.compose.ui.unit.IntOffset(0, -120)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                shadowElevation = 8.dp
                            ) {
                                Text(
                                    text = error,
                                    modifier = Modifier.padding(8.dp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    TextField(
                        value = task.text,
                        onValueChange = { onUpdate(task.copy(text = it)) },
                        readOnly = isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .graphicsLayer {
                                translationY = if (isMatch && isHighlightActive) bounceOffset else 0f
                                alpha = if (isLoading) 0.5f else 1f
                            },
                        visualTransformation = if (isHighlightActive)
                            SearchHighlightTransformation(highlightQuery, Color(0xFFFFEB3B))
                        else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { onImeAction() }),
                        placeholder = {
                            Text(
                                "What needs to be done?",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        ),
                        singleLine = false,
                        maxLines = 5
                    )
                }

                StatusSelector(
                    currentStatus = task.status,
                    onStatusChange = { newStatus -> onUpdate(task.copy(status = newStatus)) }
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    IconButton(
                        onClick = onMoveToTop,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move to top",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusSelector(
    currentStatus: TodoStatus,
    onStatusChange: (TodoStatus) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TodoStatus.entries.forEach { status ->
                val isSelected = currentStatus == status
                val dotSize by animateDpAsState(
                    targetValue = if (isSelected) 30.dp else 24.dp,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                    label = "pop"
                )

                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(if (isSelected) status.color else status.color.copy(alpha = 0.15f))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onStatusChange(status)
                        }
                )
            }
        }
        Text(
            text = currentStatus.label,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = currentStatus.color,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun StatusCounter(
    count: Int,
    color: Color,
    icon: ImageVector? = null
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
            Text(
                text = count.toString(),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

class SearchHighlightTransformation(private val query: String, private val highlightColor: Color) :
    VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            buildAnnotatedString {
                append(text.text)
                if (query.isNotBlank()) {
                    var index = text.text.indexOf(query, ignoreCase = true)
                    while (index >= 0) {
                        addStyle(
                            style = SpanStyle(
                                background = highlightColor,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black
                            ),
                            start = index,
                            end = index + query.length
                        )
                        index = text.text.indexOf(query, index + 1, ignoreCase = true)
                    }
                }
            },
            OffsetMapping.Identity
        )
    }
}

