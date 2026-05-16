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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mytodoapp.components.TodoAlertDialog
import com.example.mytodoapp.data.TodoGroup
import com.example.mytodoapp.data.TodoStatus
import com.example.mytodoapp.utils.ImportState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mytodoapp.auth.AuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    groups: List<TodoGroup>,
    importState: ImportState = ImportState.Idle,
    onResetImportState: () -> Unit = {},
    softDeleteGroupId: String? = null,
    onSoftDeleteHandled: () -> Unit = {},
    authManager: com.example.mytodoapp.auth.AuthManager,
    isSyncing: Boolean = false,
    onNavigateToEdit: (TodoGroup, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onDeleteGroup: (TodoGroup) -> Unit,
    onTogglePin: (TodoGroup) -> Unit
) {
    var groupToDelete by remember { mutableStateOf<TodoGroup?>(null) }
    var lastClickTime by remember { mutableStateOf(0L) }
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberLazyListState()
    val context = LocalContext.current
    
    val authState by authManager.authState.collectAsStateWithLifecycle()
    val user by authManager.currentUserFlow.collectAsStateWithLifecycle()

    // --- Import Dialogs ---
    if (importState is ImportState.Loading) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            title = { 
                Text(
                    "Restoring Data", 
                    fontWeight = FontWeight.ExtraBold, 
                    color = MaterialTheme.colorScheme.onSurface 
                ) 
            },
            text = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Please wait...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { }
        )
    } else if (importState is ImportState.Success) {
        val successState = importState as ImportState.Success
        AlertDialog(
            onDismissRequest = { onResetImportState() },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            icon = { 
                Icon(
                    imageVector = Icons.Default.CheckCircle, 
                    contentDescription = null, 
                    tint = Color(0xFF4CAF50), 
                    modifier = Modifier.size(32.dp)
                ) 
            },
            title = { 
                Text(
                    "Import Successful", 
                    fontWeight = FontWeight.ExtraBold, 
                    color = MaterialTheme.colorScheme.onSurface 
                ) 
            },
            text = { 
                Column {
                    Text("Data has been successfully restored.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• Tasks Imported: ${successState.tasksImported}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text("• Duplicates Ignored: ${successState.duplicatesIgnored}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Your settings have also been updated.", 
                        style = MaterialTheme.typography.labelMedium, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onResetImportState() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Great", fontWeight = FontWeight.Bold)
                }
            }
        )
    } else if (importState is ImportState.Error) {
        val errorState = importState as ImportState.Error
        AlertDialog(
            onDismissRequest = { onResetImportState() },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            icon = { 
                Icon(
                    imageVector = Icons.Default.Warning, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                ) 
            },
            title = { 
                Text(
                    "Import Failed", 
                    fontWeight = FontWeight.ExtraBold, 
                    color = MaterialTheme.colorScheme.onSurface 
                ) 
            },
            text = { Text(errorState.message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = { onResetImportState() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- Soft Delete Logic ---
    val pendingDeletions = remember { mutableStateMapOf<String, TodoGroup>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var activeDeletionJob by remember { mutableStateOf<Job?>(null) }
    var itemToScrollTo by remember { mutableStateOf<String?>(null) }

    // Clean up pending deletions if they have actually been removed from 'groups'
    LaunchedEffect(groups) {
        val currentGroupIds = groups.map { it.id }.toSet()
        val idsToRemove = pendingDeletions.keys.filter { it !in currentGroupIds }
        idsToRemove.forEach { pendingDeletions.remove(it) }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeDeletionJob?.cancel()
            val groupsToDelete = pendingDeletions.values.toList()
            groupsToDelete.forEach { group ->
                onDeleteGroup(group)
            }
        }
    }

    val performSoftDelete: (TodoGroup) -> Unit = { group ->
        activeDeletionJob?.cancel()

        // Just flush previously pending to DB, but keep them in pendingDeletions 
        // until the DB actually updates to prevent flickering.
        pendingDeletions.values.filter { it.id != group.id }.forEach { pendingGroup ->
            onDeleteGroup(pendingGroup)
        }

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
                    val pendingGroup = pendingDeletions[group.id]
                    if (pendingGroup != null) {
                        onDeleteGroup(pendingGroup)
                        // Notice we don't immediately remove it from pendingDeletions here.
                        // The LaunchedEffect(groups) above will remove it once the flow updates.
                    }
                }
            }
        }
    }

    LaunchedEffect(softDeleteGroupId) {
        if (softDeleteGroupId != null) {
            val groupToSoftDelete = groups.find { it.id == softDeleteGroupId }
            if (groupToSoftDelete != null) {
                performSoftDelete(groupToSoftDelete)
            }
            onSoftDeleteHandled()
        }
    }

    // ✅ AUTO-SCROLL TO TOP ON INITIAL LOAD OR IMPORT SUCCESS
    LaunchedEffect(groups.isNotEmpty(), importState) {
        if (groups.isNotEmpty() && (importState is ImportState.Success || groups.size <= 5)) {
            scrollState.animateScrollToItem(0)
        }
    }

    val visibleGroups = groups.filterNot { pendingDeletions.containsKey(it.id) }

    val sortedGroups = remember(visibleGroups) {
        visibleGroups.sortedWith(
            compareByDescending<TodoGroup> { it.isPinned }
                .thenByDescending { it.createdAt }
        )
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
                                IconButton(onClick = {
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastClickTime > 500) {
                                        lastClickTime = currentTime
                                        focusManager.clearFocus()
                                        onNavigateToSettings()
                                    }
                                }) {
                                    val googleProvider = user?.providerData?.find { it.providerId == "google.com" }
                                    val photoUrl = googleProvider?.photoUrl ?: user?.photoUrl

                                    if (photoUrl != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(photoUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Profile",
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = "Settings",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }                                }
                                
                                val isGuest = authState == AuthState.GUEST || user == null || user!!.isAnonymous
                                
                                val googleProvider = user?.providerData?.find { it.providerId == "google.com" }
                                val displayName = googleProvider?.displayName ?: user?.displayName
                                val firstName = displayName?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
                                
                                val titleText = if (isGuest || firstName == null) "My Workspace" else "$firstName's Workspace"
                                
                                Text(titleText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
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
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime > 500) {
                            lastClickTime = currentTime
                            focusManager.clearFocus()
                            onNavigateToEdit(TodoGroup(), "") 
                        }
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
                text = "Are you sure you want to delete '${groupToDelete?.title?.ifBlank { "Untitled" } ?: "Untitled"}'?",
                confirmText = "Delete",
                onConfirm = {
                    val target = groupToDelete!!
                    groupToDelete = null
                    performSoftDelete(target)
                },
                onDismiss = { groupToDelete = null }
            )
        }

        if (isSyncing && groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Syncing your workspace...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Retrieving your projects and tasks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                        onTogglePin = onTogglePin,
                        onRequestDelete = { groupToDelete = it },
                        haptic = haptic,
                        onNavigate = { g, q ->
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime > 500) {
                                lastClickTime = currentTime
                                onNavigateToEdit(g, q)
                            }
                        }
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
    onNavigate: (TodoGroup, String) -> Unit,
    onTogglePin: (TodoGroup) -> Unit,
    onRequestDelete: (TodoGroup) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
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
        enableDismissFromEndToStart = group.tasks.isNotEmpty(),
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
                if (group.tasks.isNotEmpty()) {
                    focusManager.clearFocus()
                    val queryToHighlight = if (isSearchActive && searchQuery.trim().isNotBlank()) searchQuery else ""
                    onNavigate(group, queryToHighlight)
                }
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
                            val taskText = if (group.tasks.isEmpty()) {
                                "Loading tasks..."
                            } else {
                                "${group.tasks.size} Tasks"
                            }
                            
                            Text(
                                text = taskText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Gray
                            )
                            Spacer(Modifier.width(8.dp))
                            CompactStatusSummary(group = group)
                        }
                    }

                    IconButton(onClick = {
                        if (group.tasks.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTogglePin(group)
                        }
                    }) {
                        Icon(
                            imageVector = if (group.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (group.isPinned) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = {
                        if (group.tasks.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRequestDelete(group)
                        }
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