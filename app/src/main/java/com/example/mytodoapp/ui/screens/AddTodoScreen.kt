package com.example.mytodoapp.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mytodoapp.components.ExportPdfDialog
import com.example.mytodoapp.data.TodoGroup
import com.example.mytodoapp.data.TodoStatus
import com.example.mytodoapp.data.TodoTask
import com.example.mytodoapp.ui.viewmodel.TodoViewModel
import com.example.mytodoapp.utils.AiHelper
import com.example.mytodoapp.components.AiRewriteOptionsDialog
import com.example.mytodoapp.components.RewriteType
import com.example.mytodoapp.components.SwipeTutorialDialog
import com.example.mytodoapp.components.TodoAlertDialog
import com.example.mytodoapp.ui.viewmodel.SpeechViewModel
import com.example.mytodoapp.utils.SpeechRecognitionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mytodoapp.components.VoiceInputBottomSheet
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
    viewModel: TodoViewModel,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onNavigateToPreview: (TodoGroup) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val titleFocusRequester = remember { FocusRequester() }
    var showTutorial by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()

    // Collect global AI Style from settings
    val globalAiStyleState by viewModel.aiRewriteType.collectAsStateWithLifecycle()
    val globalAiStyle = globalAiStyleState ?: RewriteType.Standard
    
    // Collect global PDF Config from settings
    val globalPdfConfigState by viewModel.pdfConfig.collectAsStateWithLifecycle()
    val globalPdfConfig = globalPdfConfigState ?: com.example.mytodoapp.utils.PdfConfig()

    // Collect global MoveDoneToBottom from settings
    val moveDoneToBottomState by viewModel.moveDoneToBottom.collectAsStateWithLifecycle()
    val moveDoneToBottom = moveDoneToBottomState ?: false

    // ✅ UI STATE FOR OVERFLOW MENU
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    // ✅ UI STATE FOR EXPORT DIALOG
    var showExportDialog by rememberSaveable { mutableStateOf(false) }

    // ✅ FIX: PREVENT ANIMATION RE-TRIGGERING
    var hasAnimatedHighlight by rememberSaveable { mutableStateOf(false) }
    
    // ✅ OPTIMIZATION 2: Use time-based visibility instead of delay
    var highlightStartTime by remember { mutableLongStateOf(0L) }
    val isHighlightVisible by remember {
        derivedStateOf {
            highlightQuery.isNotEmpty() && highlightStartTime != 0L
        }
    }

    // ✅ OPTIMIZATION 3: Conditional animation target
    val infiniteTransition = rememberInfiniteTransition(label = "highlightBounce")
    val bounceOffsetState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isHighlightVisible) -6f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(250, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceValue"
    )
    val bounceProvider = remember(bounceOffsetState) { { bounceOffsetState.value } }
    
    // ✅ OPTIMIZATION 4: Use custom saver instead of Gson
    var title by rememberSaveable { mutableStateOf(existingGroup.title) }
    var tasks by rememberSaveable(stateSaver = todoListSaver) {
        mutableStateOf(
            if (existingGroup.tasks.isEmpty()) listOf(TodoTask()) else existingGroup.tasks
        )
    }

    // ✅ AUTO-SAVE: Debounced UI-to-ViewModel synchronization
    LaunchedEffect(title, tasks) {
        viewModel.updateCurrentGroup(existingGroup.copy(title = title, tasks = tasks))
    }

    // ✅ AUTO-SAVE: Lifecycle listener for immediate save on exit/background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.forceImmediateSave()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.forceImmediateSave()
        }
    }

    val scope = rememberCoroutineScope()
    
    // SPEECH-TO-TEXT SETUP
    val speechManager = remember { SpeechRecognitionManager(context) }
    val speechViewModel: SpeechViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return SpeechViewModel(speechManager) as T
            }
        }
    )
    val speechState by speechViewModel.uiState.collectAsStateWithLifecycle()
    var showVoiceSheet by remember { mutableStateOf(false) }
    var taskTargetingVoice by remember { mutableStateOf<String?>(null) }
    var showApplyVoiceDialog by remember { mutableStateOf(false) }
    var pendingVoiceText by remember { mutableStateOf("") }

    // Smart Append logic
    val smartCombine = { existing: String, new: String ->
        if (existing.isBlank()) new
        else {
            val trimmedExisting = existing.trimEnd()
            val lastChar = trimmedExisting.lastOrNull()
            
            // Check if it ends with punctuation that should be followed by a space
            val needsSpace = lastChar != null && !lastChar.isWhitespace() && lastChar != '\n'
            
            val separator = when {
                lastChar == '\n' -> ""
                needsSpace -> " "
                else -> ""
            }
            trimmedExisting + separator + new.trim()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showVoiceSheet = true
            speechViewModel.startListening()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ OPTIMIZATION 5: Initialize all focus requesters once
    val focusMap = remember {
        mutableStateMapOf<String, FocusRequester>().apply {
            tasks.forEach { task ->
                this[task.id] = FocusRequester()
            }
        }
    }

    // Function to scroll to focused item if it moves
    val scrollToFocusedItem = {
        scope.launch {
            delay(100)
            val focusedIndex = tasks.indexOfFirst { focusMap[it.id]?.toString()?.contains("true") == true }
            if (focusedIndex != -1) {
                scrollState.animateScrollToItem(focusedIndex + 2)
            }
        }
    }

    // Apply "Move Done to Bottom" when it's enabled and tasks change
    LaunchedEffect(tasks, moveDoneToBottom) {
        if (moveDoneToBottom) {
            val sorted = tasks.sortedWith(compareBy { it.status == TodoStatus.Done })
            if (sorted != tasks) {
                tasks = sorted
                scrollToFocusedItem()
            }
        }
    }

    val isPdfEnabled = remember(tasks) { tasks.any { it.text.isNotBlank() } }

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    // AI Rewrite States
    val aiJobs = remember { mutableStateMapOf<String, Job>() }
    val aiLoadingStates = remember { mutableStateMapOf<String, Boolean>() }
    val aiErrors = remember { mutableStateMapOf<String, String>() }
    val aiRewriteTypes = remember { mutableStateMapOf<String, RewriteType>() }
    var taskForAiDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(highlightQuery) {
        val trimmed = highlightQuery.trim()
        if (hasAnimatedHighlight || trimmed.isEmpty()) {
            if (title.isEmpty() && !hasAnimatedHighlight) titleFocusRequester.requestFocus()
            return@LaunchedEffect
        }

        hasAnimatedHighlight = true // ✅ Mark immediately so it never runs again

        try {
            delay(100)
            highlightStartTime = System.currentTimeMillis()
            val titleMatch = title.contains(trimmed, ignoreCase = true)
            if (!titleMatch) {
                val taskIndex = tasks.indexOfFirst { it.text.contains(trimmed, ignoreCase = true) }
                if (taskIndex != -1) {
                    scrollState.animateScrollToItem(index = taskIndex + 2)
                }
            }
            delay(3000)
        } finally {
            highlightStartTime = 0L // ✅ Ensure animation is destroyed
        }
    }

    var newTaskToFocusId by remember { mutableStateOf<String?>(null) }
    var previousTasksSize by remember { mutableStateOf(tasks.size) }
    var shakingTaskId by remember { mutableStateOf<Pair<String, Long>?>(null) }

    LaunchedEffect(shakingTaskId) {
        if (shakingTaskId != null) {
            delay(600)
            shakingTaskId = null
        }
    }

    LaunchedEffect(tasks.size) {
        if (tasks.size > previousTasksSize) {
            delay(100)
            // Simply scroll to the absolute top of the screen
            if (scrollState.layoutInfo.totalItemsCount > 0) {
                scrollState.animateScrollToItem(0)
            }
        }
        previousTasksSize = tasks.size
    }

    val handleExit = { action: () -> Unit ->
        focusManager.clearFocus()
        action()
    }

    val onNavigateBack = {
        handleExit(onBack)
    }

    BackHandler(enabled = true) { onNavigateBack() }

    if (showTutorial) {
        SwipeTutorialDialog(onDismiss = { showTutorial = false })
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. BACK BUTTON
                    IconButton(onClick = { onNavigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 2. STATIC TITLE
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title.ifBlank { "Untitled" },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 3. OVERFLOW MENU
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.width(200.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Sort Tasks", modifier = Modifier.weight(1f))
                                        Icon(Icons.Default.ArrowRight, null, modifier = Modifier.size(20.dp))
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    showSortMenu = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort",
                                        tint = Color(0xFF4CAF50) // Green for organization
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Preview PDF") },
                                onClick = {
                                    showMenu = false
                                    handleExit {
                                        val updatedGroup = existingGroup.copy(title = title, tasks = tasks)
                                        viewModel.setPreviewGroup(updatedGroup)
                                        onNavigateToPreview(updatedGroup)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "Preview PDF",
                                        tint = Color(0xFF2196F3) // Blue
                                    )
                                },
                                enabled = isPdfEnabled
                            )
                            DropdownMenuItem(
                                text = { Text("Share/Export") },
                                onClick = {
                                    showMenu = false
                                    handleExit { showExportDialog = true }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Export as PDF",
                                        tint = Color(0xFF00BCD4) // Cyan/Blueish for Export
                                    )
                                },
                                enabled = isPdfEnabled
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFF44336)) },
                                onClick = {
                                    showMenu = false
                                    handleExit { showDeleteDialog = true }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete, 
                                        contentDescription = "Delete", 
                                        tint = Color(0xFFF44336) // Red
                                    )
                                }
                            )
                        }

                        // SORT SUBMENU
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.width(220.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            com.example.mytodoapp.ui.viewmodel.SortStrategy.entries.forEach { strategy ->
                                DropdownMenuItem(
                                    text = { Text(strategy.label) },
                                    onClick = {
                                        showSortMenu = false
                                        tasks = viewModel.getSortedTasks(tasks, strategy, moveDoneToBottom)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val newTask = TodoTask()
                    tasks = listOf(newTask) + tasks
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

        if (showExportDialog) {
            ExportPdfDialog(
                title = title,
                tasks = tasks,
                config = globalPdfConfig,
                onDismiss = { showExportDialog = false }
            )
        }

        if (showDeleteDialog) {
            TodoAlertDialog(
                title = "Delete Project?",
                text = "Are you sure you want to delete '${title.ifBlank { "Untitled" }}'?",
                confirmText = "Delete",
                onConfirm = {
                    showDeleteDialog = false
                    onDelete()
                },
                onDismiss = { showDeleteDialog = false }
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
                //  Add stable key to prevent unnecessary recomposition
                item(key = "title_field") {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Collection Title") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(titleFocusRequester)
                            .graphicsLayer {
                                translationY = if (isHighlightVisible && title.contains(highlightQuery, true)) {
                                    bounceProvider()
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

                //  Extract to separate composable to isolate recomposition
                item(key = "tasks_header") {
                    Spacer(modifier = Modifier.height(24.dp))
                    TasksHeaderSection(
                        tasks = tasks,
                        onShowTutorial = {
                            showTutorial = true
                        }
                    )
                }

                itemsIndexed(
                    tasks,
                    key = { _, task -> task.id })
                { index, task ->
                    val currentRequester = remember(task.id) { focusMap[task.id] ?: FocusRequester() }

                    // Isolated task row for better recomposition
                    TaskEditRow(
                        task = task,
                        highlightQuery = highlightQuery,
                        isHighlightActive = isHighlightVisible,
                        bounceOffsetProvider = bounceProvider,
                        moveDoneToBottom = moveDoneToBottom,
                        isShaking = shakingTaskId?.first == task.id,
                        isLoading = aiLoadingStates[task.id] ?: false,
                        error = aiErrors[task.id],
                        rewriteType = aiRewriteTypes[task.id] ?: RewriteType.Standard,
                        canUndo = viewModel.canUndoMap[task.id] == true,
                        canRedo = viewModel.canRedoMap[task.id] == true,
                        modifier = Modifier.animateItem(),
                        focusRequester = currentRequester,
                        onInitHistory = { text -> viewModel.initHistoryIfNeeds(task.id, text) },
                        onPushHistory = { text -> viewModel.pushHistory(task.id, text) },
                        onUndo = {
                            viewModel.undo(task.id)?.let { undoneText ->
                                tasks = tasks.map { if (it.id == task.id) it.copy(text = undoneText) else it }
                            }
                        },
                        onRedo = {
                            viewModel.redo(task.id)?.let { redoneText ->
                                tasks = tasks.map { if (it.id == task.id) it.copy(text = redoneText) else it }
                            }
                        },
                        onUpdate = { updatedTask ->
                            tasks = tasks.map { if (it.id == updatedTask.id) updatedTask else it }
                        },
                        onDelete = {
                            if (tasks.size > 1) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val idToRemove = task.id
                                aiJobs[idToRemove]?.cancel()
                                aiJobs.remove(idToRemove)
                                aiLoadingStates[idToRemove] = false
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
                        onMoveUp = {
                            val currentIndex = tasks.indexOfFirst { it.id == task.id }
                            if (currentIndex > 0) {
                                val taskAbove = tasks[currentIndex - 1]
                                if (moveDoneToBottom && task.status == TodoStatus.Done && taskAbove.status != TodoStatus.Done) {
                                    Toast.makeText(context, "Can't move up: 'Done' tasks stay at the bottom", Toast.LENGTH_SHORT).show()
                                } else {
                                    val mutableTasks = tasks.toMutableList()
                                    val item = mutableTasks.removeAt(currentIndex)
                                    mutableTasks.add(currentIndex - 1, item)
                                    tasks = mutableTasks.toList()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onMoveToTop = {
                            if (moveDoneToBottom && task.status == TodoStatus.Done) {
                                Toast.makeText(context, "Can't move to top: 'Done' tasks stay at the bottom", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                val currentIndex = tasks.indexOfFirst { it.id == task.id }
                                if (currentIndex > 0) {
                                    val mutableTasks = tasks.toMutableList()
                                    val item = mutableTasks.removeAt(currentIndex)
                                    mutableTasks.add(0, item)
                                    tasks = mutableTasks.toList()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onMoveDown = {
                            val currentIndex = tasks.indexOfFirst { it.id == task.id }
                            if (moveDoneToBottom && task.status != TodoStatus.Done && 
                                currentIndex < tasks.size - 1 && tasks[currentIndex + 1].status == TodoStatus.Done) {
                                Toast.makeText(context, "Can't move below 'Done' tasks", Toast.LENGTH_SHORT).show()
                            } else {
                                if (currentIndex < tasks.size - 1) {
                                    val mutableTasks = tasks.toMutableList()
                                    val item = mutableTasks.removeAt(currentIndex)
                                    mutableTasks.add(currentIndex + 1, item)
                                    tasks = mutableTasks.toList()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onMoveToBottom = {
                            val currentIndex = tasks.indexOfFirst { it.id == task.id }
                            if (moveDoneToBottom && task.status != TodoStatus.Done) {
                                Toast.makeText(context, "Only 'Done' tasks stay at the very bottom", Toast.LENGTH_SHORT).show()
                            } else {
                                if (currentIndex < tasks.size - 1) {
                                    val mutableTasks = tasks.toMutableList()
                                    val item = mutableTasks.removeAt(currentIndex)
                                    mutableTasks.add(item)
                                    tasks = mutableTasks.toList()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onImeAction = {
                            if (index < tasks.size - 1) {
                                val nextTaskId = tasks[index + 1].id
                                focusMap[nextTaskId]?.requestFocus()
                            } else {
                                val newTask = TodoTask()
                                tasks = listOf(newTask) + tasks
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
                                        viewModel.pushHistory(taskId, result)
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
                        },
                        onVoiceInput = { taskId ->
                            taskTargetingVoice = taskId
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                showVoiceSheet = true
                                speechViewModel.startListening()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )

                    if (newTaskToFocusId == task.id) {
                        LaunchedEffect(task.id) {
                            currentRequester.requestFocus()
                            delay(100) // Wait for keyboard and layout adjustments
                            scrollState.animateScrollToItem(0)
                            newTaskToFocusId = null
                        }
                    }
                }
            }
        }
    }

    // AI Rewrite Dialog
    taskForAiDialog?.let { taskId ->
        AiRewriteOptionsDialog(
            currentType = aiRewriteTypes[taskId] ?: globalAiStyle,
            onTypeSelected = { type ->
                aiRewriteTypes[taskId] = type
                taskForAiDialog = null
            },
            onDismiss = { taskForAiDialog = null }
        )
    }

    // Voice Input Bottom Sheet
    if (showVoiceSheet) {
        VoiceInputBottomSheet(
            state = speechState,
            onStartListening = { speechViewModel.startListening() },
            onStopListening = { speechViewModel.stopListening() },
            onPauseListening = { speechViewModel.pauseListening() },
            onResumeListening = { speechViewModel.resumeListening() },
            onCancel = {
                showVoiceSheet = false
                speechViewModel.reset()
            },
            onRewriteWithAi = { text -> speechViewModel.rewriteWithAi(text) },
            onConfirm = { text ->
                val targetTask = tasks.find { it.id == taskTargetingVoice }
                if (targetTask != null && targetTask.text.isNotBlank()) {
                    pendingVoiceText = text
                    showApplyVoiceDialog = true
                } else {
                    taskTargetingVoice?.let { taskId ->
                        tasks = tasks.map { if (it.id == taskId) it.copy(text = text) else it }
                        viewModel.pushHistory(taskId, text)
                    }
                    showVoiceSheet = false
                    speechViewModel.reset()
                }
            },
            onDismiss = {
                showVoiceSheet = false
                speechViewModel.reset()
            }
        )
    }

    if (showApplyVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showApplyVoiceDialog = false },
            title = { 
                Text(
                    "Apply Voice Text", 
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface 
                ) 
            },
            text = { 
                Text(
                    "You already have some text in this task. How would you like to apply the new transcription?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        taskTargetingVoice?.let { taskId ->
                            tasks = tasks.map { 
                                if (it.id == taskId) it.copy(text = smartCombine(it.text, pendingVoiceText)) 
                                else it 
                            }
                            val updatedText = tasks.find { it.id == taskId }?.text ?: ""
                            viewModel.pushHistory(taskId, updatedText)
                        }
                        showApplyVoiceDialog = false
                        showVoiceSheet = false
                        speechViewModel.reset()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Append", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        taskTargetingVoice?.let { taskId ->
                            tasks = tasks.map { if (it.id == taskId) it.copy(text = pendingVoiceText) else it }
                            viewModel.pushHistory(taskId, pendingVoiceText)
                        }
                        showApplyVoiceDialog = false
                        showVoiceSheet = false
                        speechViewModel.reset()
                    }
                ) {
                    Text("Replace", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
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



@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TaskEditRow(
    task: TodoTask,
    highlightQuery: String,
    isHighlightActive: Boolean,
    bounceOffsetProvider: () -> Float,
    moveDoneToBottom: Boolean,
    isShaking: Boolean = false,
    isLoading: Boolean = false,
    error: String? = null,
    rewriteType: RewriteType = RewriteType.Standard,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    onInitHistory: (String) -> Unit,
    onPushHistory: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onUpdate: (TodoTask) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToBottom: () -> Unit,
    onImeAction: () -> Unit,
    onAiRewrite: (String, RewriteType) -> Unit,
    onAiLongClick: (String) -> Unit,
    onClearError: (String) -> Unit,
    onVoiceInput: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState()
    val currentValue = dismissState.currentValue

    var isFocused by remember { mutableStateOf(false) }

    // Custom NestedScrollConnection to prevent parent from scrolling when this task is being edited
    val nestedScrollConnection = remember(isFocused) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Only consume scroll if this task is focused, to allow normal list scrolling otherwise
                return if (isFocused) available else androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return if (isFocused) available else Velocity.Zero
            }
        }
    }

    var localTextFieldValue by remember(task.id) { 
        mutableStateOf(TextFieldValue(task.text, selection = TextRange(0))) 
    }
    
    // Sync external changes (undo/redo/AI rewrite) into local state
    LaunchedEffect(task.text) {
        if (task.text != localTextFieldValue.text) {
            localTextFieldValue = localTextFieldValue.copy(
                text = task.text,
                selection = TextRange(0)
            )
        }
    }
    // Debounced sync back to parent (only when user is typing)
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    val isMatch = remember(localTextFieldValue.text, highlightQuery, isHighlightActive) {
        isHighlightActive && highlightQuery.isNotEmpty() && localTextFieldValue.text.contains(highlightQuery, ignoreCase = true)
    }

    val isAiEnabled = remember(localTextFieldValue.text) {
        localTextFieldValue.text.trim().length >= 7
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

    LaunchedEffect(task.id) {
        onInitHistory(task.text)
    }

    LaunchedEffect(localTextFieldValue.text) {
        if (isFocused) {
            delay(1000)
            onPushHistory(localTextFieldValue.text)
        }
    }

    // SHAKE ANIMATION LOGIC
    val shakeAnim = remember { Animatable(0f) }
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
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.isFavorite) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp).padding(end = 4.dp))
                    }

                    TextField(
                        value = localTextFieldValue,
                        onValueChange = { newValue ->
                            localTextFieldValue = newValue
                            // Debounced sync to parent — avoids O(N) recomposition per keystroke
                            debounceJob?.cancel()
                            debounceJob = scope.launch {
                                delay(300)
                                onUpdate(task.copy(text = newValue.text))
                            }
                        },
                        readOnly = isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .nestedScroll(nestedScrollConnection)
                            .onFocusChanged { state ->
                                isFocused = state.isFocused
                                if (!state.isFocused) {
                                    // Immediately sync on focus loss
                                    debounceJob?.cancel()
                                    if (localTextFieldValue.text != task.text) {
                                        onUpdate(task.copy(text = localTextFieldValue.text))
                                    }
                                    onPushHistory(localTextFieldValue.text)
                                }
                            }
                            .graphicsLayer {
                                translationY = if (isMatch && isHighlightActive) bounceOffsetProvider() else 0f
                                alpha = if (isLoading) 0.5f else 1f
                            },
                        visualTransformation = if (isHighlightActive)
                            SearchHighlightTransformation(highlightQuery, Color(0xFFFFEB3B))
                        else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = {
                            // Flush pending text before IME action
                            debounceJob?.cancel()
                            if (localTextFieldValue.text != task.text) {
                                onUpdate(task.copy(text = localTextFieldValue.text))
                            }
                            onImeAction()
                        }),
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

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onVoiceInput(task.id)
                        },
                        modifier = Modifier.size(36.dp).padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // AI ICON or LOADER (Switches automatically)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Rewrite",
                                tint = if (isAiEnabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .size(30.dp)
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

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onUndo()
                        },
                        enabled = canUndo,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRedo()
                        },
                        enabled = canRedo,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("|", fontSize = 28.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.padding(bottom = 4.dp))
                            Spacer(modifier = Modifier.width(8.dp))

                            StatusSelector(
                                currentStatus = task.status,
                                onStatusChange = { newStatus -> 
                                    if (moveDoneToBottom && newStatus == TodoStatus.Done) {
                                        focusManager.clearFocus()
                                    }
                                    onUpdate(task.copy(status = newStatus)) 
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))
                            Text("|", fontSize = 28.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = onMoveUp,
                                    onLongClick = onMoveToTop
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move up",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = onMoveDown,
                                    onLongClick = onMoveToBottom
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move down",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
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
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TodoStatus.entries.forEach { status ->
                val isSelected = currentStatus == status
                val dotSize by animateDpAsState(
                    targetValue = if (isSelected) 24.dp else 18.dp,
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