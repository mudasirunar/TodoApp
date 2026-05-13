package com.example.mytodoapp.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytodoapp.components.TodoAlertDialog
import com.example.mytodoapp.data.TodoGroup
import com.example.mytodoapp.data.TodoStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    groups: List<TodoGroup>?,
    softDeleteGroupId: String? = null,
    onSoftDeleteHandled: () -> Unit = {},
    onNavigateToEdit: (TodoGroup, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onDeleteGroup: (TodoGroup) -> Unit,
    onTogglePin: (TodoGroup) -> Unit
) {
    var groupToDelete by remember { mutableStateOf<TodoGroup?>(null) }
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberLazyListState()

    // --- Soft Delete Logic ---
    val pendingDeletions = remember { mutableStateMapOf<String, TodoGroup>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var activeDeletionJob by remember { mutableStateOf<Job?>(null) }
    var itemToScrollTo by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activeDeletionJob?.cancel()
            pendingDeletions.values.forEach { group ->
                onDeleteGroup(group)
            }
            pendingDeletions.clear()
        }
    }

    val performSoftDelete: (TodoGroup) -> Unit = { group ->
        activeDeletionJob?.cancel()

        pendingDeletions.values.forEach { pendingGroup ->
            onDeleteGroup(pendingGroup)
        }
        pendingDeletions.clear()

        pendingDeletions[group.id] = group

        activeDeletionJob = coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Deleted '${group.title.ifBlank { "Untitled" }}'",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )

            when (result) {
                SnackbarResult.ActionPerformed -> {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    pendingDeletions.remove(group.id)
                    itemToScrollTo = group.id
                }
                SnackbarResult.Dismissed -> {
                    val pendingGroup = pendingDeletions.remove(group.id)
                    if (pendingGroup != null) {
                        onDeleteGroup(pendingGroup)
                    }
                }
            }
        }
    }

    LaunchedEffect(softDeleteGroupId) {
        if (softDeleteGroupId != null) {
            val groupToSoftDelete = groups?.find { it.id == softDeleteGroupId }
            if (groupToSoftDelete != null) {
                performSoftDelete(groupToSoftDelete)
            }
            onSoftDeleteHandled()
        }
    }

    val visibleGroups = groups?.filterNot { pendingDeletions.containsKey(it.id) }

    val sortedGroups = remember(visibleGroups) {
        visibleGroups?.sortedWith(
            compareByDescending<TodoGroup> { it.isPinned }
                .thenByDescending { it.createdAt }
        ) ?: emptyList()
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredGroups = remember(sortedGroups, searchQuery) {
        val trimmedQuery = searchQuery.trim()

        if (trimmedQuery.isEmpty()) {
            sortedGroups
        } else {
            sortedGroups.filter { group ->
                val displayTitle = group.title.ifBlank { "Untitled" }

                displayTitle.contains(trimmedQuery, ignoreCase = true) ||
                        group.tasks.any { it.text.contains(trimmedQuery, ignoreCase = true) }
            }
        }
    }

    LaunchedEffect(itemToScrollTo, filteredGroups) {
        val targetId = itemToScrollTo
        if (targetId != null) {
            val index = filteredGroups.indexOfFirst { it.id == targetId }
            if (index != -1) {
                delay(200)
                
                val visibleItems = scrollState.layoutInfo.visibleItemsInfo
                val itemInfo = visibleItems.find { it.key == targetId }
                val viewportHeight = scrollState.layoutInfo.viewportSize.height
                
                val isVisuallyHidden = itemInfo == null || 
                        itemInfo.offset < 0 || 
                        itemInfo.offset + (itemInfo.size / 2) > viewportHeight

                if (isVisuallyHidden) {
                    scrollState.animateScrollToItem(index)
                }
                itemToScrollTo = null
            }
        }
    }

    var previousIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }
    var fabExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset) {
        fabExpanded = if (scrollState.firstVisibleItemIndex > previousIndex) {
            false
        } else if (scrollState.firstVisibleItemIndex < previousIndex) {
            true
        } else {
            scrollState.firstVisibleItemScrollOffset <= previousScrollOffset
        }
        previousIndex = scrollState.firstVisibleItemIndex
        previousScrollOffset = scrollState.firstVisibleItemScrollOffset
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    val bottomPadding by animateDpAsState(
        targetValue = if (fabExpanded) 0.dp else 16.dp,
        label = "snackbarPadding"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = bottomPadding)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = isSearchActive,
                        transitionSpec = {
                            (fadeIn(tween(300)) + slideInHorizontally { it / 2 })
                                .togetherWith(fadeOut(tween(300)) + slideOutHorizontally { -it / 2 })
                        },
                        label = "searchAnimation"
                    ) { active ->
                        if (active) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search title or tasks...", fontSize = 16.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp)
                                    .focusRequester(focusRequester),
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            searchQuery = ""
                                            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear text",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                ),
                                textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onNavigateToSettings,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Text("My Workspace", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            searchQuery = ""
                            focusManager.clearFocus()
                        }
                    }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabExpanded,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { 
                        focusManager.clearFocus()
                        onNavigateToEdit(TodoGroup(), "") 
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(28.dp))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ){ padding ->
        if (groupToDelete != null) {
            TodoAlertDialog(
                title = "Delete Project?",
                text = "Are you sure you want to delete '${groupToDelete?.title}'?",
                confirmText = "Delete",
                onConfirm = {
                    val target = groupToDelete!!
                    groupToDelete = null
                    performSoftDelete(target)
                },
                onDismiss = { groupToDelete = null }
            )
        }

        if (groups == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else if (groups.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                Text("Your slate is clean", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Tap + to add your first project", color = Color.Gray)
            }
        } else if (filteredGroups.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                Text("No matches found", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Try searching for something else", color = Color.Gray)
            }
        }else {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredGroups, key = { it.id }) { group ->
                    GroupCardItem(
                        group = group,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        onNavigateToEdit = onNavigateToEdit,
                        onTogglePin = onTogglePin,
                        onRequestDelete = { groupToDelete = it },
                        haptic = haptic
                    )
                }
            }
        }
    }
}

private val GoldColor = Color(0xFFFFD700)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LazyItemScope.GroupCardItem(
    group: TodoGroup,
    isSearchActive: Boolean,
    searchQuery: String,
    onNavigateToEdit: (TodoGroup, String) -> Unit,
    onTogglePin: (TodoGroup) -> Unit,
    onRequestDelete: (TodoGroup) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val focusManager = LocalFocusManager.current
    val favCount = remember(group.tasks) { group.tasks.count { it.isFavorite } }
    val hasFavorites = favCount > 0
    val totalCount = group.tasks.size
    val favDoneCount = remember(group.tasks) { group.tasks.count { it.isFavorite && it.status.label == "Done" } }
    val regularDoneCount = remember(group.tasks) { group.tasks.count { !it.isFavorite && it.status.label == "Done" } }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRequestDelete(group)
                false
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.animateItem(),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val isDismissed = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val color = if (isDismissed) MaterialTheme.colorScheme.error else Color.Transparent

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) {
        Card(
            onClick = {
                focusManager.clearFocus()
                val queryToHighlight = if (isSearchActive && searchQuery.trim().isNotBlank()) searchQuery else ""
                onNavigateToEdit(group, queryToHighlight)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).background(
                            if (hasFavorites) GoldColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (hasFavorites) Icons.Default.Star else Icons.Default.List,
                            null,
                            tint = if (hasFavorites) GoldColor else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                group.title.ifBlank { "Untitled" },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (hasFavorites) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(GoldColor.copy(alpha = 0.1f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, tint = GoldColor, modifier = Modifier.size(10.dp))
                                        Text(" $favCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GoldColor)
                                    }
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${group.tasks.size} Tasks",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Gray
                            )
                            Spacer(Modifier.width(8.dp))
                            CompactStatusSummary(group = group)
                        }
                    }

                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTogglePin(group)
                    }) {
                        Icon(
                            imageVector = if (group.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (group.isPinned) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRequestDelete(group)
                    }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                    }
                }

                Spacer(Modifier.height(16.dp))

                MultiColorProgressBar(
                    totalTasks = totalCount,
                    regularDoneCount = regularDoneCount,
                    favDoneCount = favDoneCount
                )
            }
        }
    }
}

@Composable
fun MultiColorProgressBar(
    totalTasks: Int,
    regularDoneCount: Int,
    favDoneCount: Int,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val gold = Color(0xFFFFD700)

    val totalDoneCount = favDoneCount + regularDoneCount
    val totalProgress = if (totalTasks > 0) totalDoneCount.toFloat() / totalTasks else 0f
    val goldRatioInDone = if (totalDoneCount > 0) favDoneCount.toFloat() / totalDoneCount else 0f

    val progressBrush = remember(favDoneCount, regularDoneCount) {
        if (favDoneCount > 0 && regularDoneCount > 0) {
            val transitionStart = (goldRatioInDone - 0.1f).coerceAtLeast(0f)
            val transitionEnd = (goldRatioInDone + 0.1f).coerceAtMost(1f)

            Brush.linearGradient(
                0.0f to gold,
                transitionStart to gold,
                transitionEnd to primaryColor,
                1.0f to primaryColor
            )
        } else if (favDoneCount > 0) {
            Brush.linearGradient(listOf(gold, gold))
        } else {
            Brush.linearGradient(listOf(primaryColor, primaryColor))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(totalProgress)
                .clip(CircleShape)
                .background(progressBrush)
        )
    }
}


@Composable
fun CompactStatusSummary(group: TodoGroup) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        TodoStatus.entries.forEach { status ->
            val count = remember(group.tasks, status) { group.tasks.count { it.status == status } }
            if (count > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(status.color)
                    )
                    Text(
                        text = count.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
