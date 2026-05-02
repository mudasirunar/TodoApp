package com.example.mytodoapp.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytodoapp.utils.TodoAlertDialog
import com.example.mytodoapp.data.TodoGroup
import com.example.mytodoapp.data.TodoStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    groups: List<TodoGroup>?,
    onNavigateToEdit: (TodoGroup, String) -> Unit,
    onDeleteGroup: (TodoGroup) -> Unit,
    onTogglePin: (TodoGroup) -> Unit
) {
    var groupToDelete by remember { mutableStateOf<TodoGroup?>(null) }
    val haptic = LocalHapticFeedback.current // 1. GET HAPTIC PROVIDER
    // 1. TRACK SCROLL STATE
    val scrollState = rememberLazyListState()

    // Inside DashboardScreen
    val sortedGroups = remember(groups) {
        groups?.sortedWith(
            compareByDescending<TodoGroup> { it.isPinned }
                .thenByDescending { it.createdAt }
        ) ?: emptyList()
    }

    val focusRequester = remember { FocusRequester() }
    // Search Feature
    val focusManager = LocalFocusManager.current
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 1. Updated Filtering Logic
    val filteredGroups = remember(sortedGroups, searchQuery) {
        val trimmedQuery = searchQuery.trim() // Handle accidental spaces

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

    // Better Logic for FAB visibility (detecting scroll delta)
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }
    var fabExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset) {
        fabExpanded = if (scrollState.firstVisibleItemIndex > previousIndex) {
            false // Scrolling Down
        } else if (scrollState.firstVisibleItemIndex < previousIndex) {
            true // Scrolling Up
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                            Text("My Workspace", fontWeight = FontWeight.ExtraBold)
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
            // 3. APPLY SMOOTH ANIMATION
            AnimatedVisibility(
                visible = fabExpanded,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { onNavigateToEdit(TodoGroup(), "") },
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
                    groupToDelete = null // Dismiss dialog instantly
                    onDeleteGroup(target)
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
                    imageVector = Icons.Default.PlaylistAdd, // Modern vector icon
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                Text("Your slate is clean", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Tap + to add your first project", color = Color.Gray)
            }
        } else if (filteredGroups.isEmpty()) {
            // ✅ NEW: Empty Search Results State
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

                    val favCount = group.tasks.count { it.isFavorite }
                    val hasFavorites = favCount > 0
                    val gold = Color(0xFFFFD700)
                    val totalCount = group.tasks.size
                    val favDoneCount = group.tasks.count { it.isFavorite && it.status.label == "Done" }
                    val regularDoneCount = group.tasks.count { !it.isFavorite && it.status.label == "Done" }

                    // 1. CREATE DISMISS STATE
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                // 2. TRIGGER VIBRATION
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                groupToDelete = group
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

                            // 3. GLOSSY BACKGROUND WITH VIBRANT RED
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
                        val completedTasks = group.tasks.count { it.status.label == "Done" }
                        val progress = if (group.tasks.isEmpty()) 0f else completedTasks.toFloat() / group.tasks.size

                        Card(
                            onClick = {
                                // Pass the query only if search is active and not blank
                                val queryToHighlight = if (isSearchActive && searchQuery.trim().isNotBlank()) searchQuery else ""
                                onNavigateToEdit(group, queryToHighlight) // Update your navigation callback to accept a String
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Subtle Icon container for extra gloss
                                    Box(
                                        Modifier.size(40.dp).background(
                                            if (hasFavorites) gold.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            CircleShape
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (hasFavorites) Icons.Default.Star else Icons.Default.List,
                                            null,
                                            tint = if (hasFavorites) gold else MaterialTheme.colorScheme.primary,
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
                                                // ✅ ADD THESE TWO LINES:
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            // 4. FAVORITE BADGE: Show a small gold star if tasks are favorited
                                            if (hasFavorites) {
                                                Spacer(Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(gold.copy(alpha = 0.1f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Star, null, tint = gold, modifier = Modifier.size(10.dp))
                                                        Text(" $favCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = gold)
                                                    }
                                                }
                                            }
                                        }
                                        // ... Inside DashboardScreen Column ...
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
                                            // The new minimalist summary
                                            CompactStatusSummary(group = group)
                                        }
                                    }

                                    // PIN BUTTON
                                    IconButton(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTogglePin(group)
                                    }) {
                                        Icon(
                                            imageVector = if (group.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = "Pin",
                                            // If pinned, use primary color; if not, use subtle gray
                                            tint = if (group.isPinned) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.6f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        groupToDelete = group
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

    // Calculate the ratios
    val totalDoneCount = favDoneCount + regularDoneCount
    val totalProgress = if (totalTasks > 0) totalDoneCount.toFloat() / totalTasks else 0f

    // Calculate the ratio of Gold within the DONE portion
    val goldRatioInDone = if (totalDoneCount > 0) favDoneCount.toFloat() / totalDoneCount else 0f

    // Create the Smooth Gradient Brush
    val progressBrush = remember(favDoneCount, regularDoneCount) {
        if (favDoneCount > 0 && regularDoneCount > 0) {
            // This creates a smooth "mix" area at the joining point
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
            .height(10.dp) // Slightly taller for better texture visibility
            .clip(CircleShape)
            .background(trackColor)
    ) {
        // The Filled Portion
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(totalProgress) // Fills based on total completion
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
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)) // Barely visible unified background
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        TodoStatus.entries.forEach { status ->
            val count = group.tasks.count { it.status == status }
            if (count > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Small vibrant dot
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(status.color)
                    )
                    // Clean, subtle text
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
