package com.example.mytodoapp.utils

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.lerp
import kotlin.math.roundToInt

@Composable
fun SwipeTutorialDialog(onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 6 })

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks to prevent dismiss
                    ),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
            Column(
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Quick Guide",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp, start = 48.dp, end = 48.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val continuousPage = pagerState.currentPage + pagerState.currentPageOffsetFraction
                            val heightDp = when {
                                continuousPage < 2f -> 220.dp
                                continuousPage > 3f -> 320.dp
                                else -> androidx.compose.ui.unit.lerp(220.dp, 320.dp, continuousPage - 2f)
                            }
                            val heightPx = heightDp.roundToPx()
                            val placeable = measurable.measure(
                                constraints.copy(minHeight = heightPx, maxHeight = heightPx)
                            )
                            layout(placeable.width, placeable.height) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                ) { page ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when (page) {
                            0, 1 -> {
                                val isToFavorite = page == 0
                                Text(
                                    text = if (isToFavorite) "Prioritize with a Swipe" else "Manage Your Highlights",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (isToFavorite)
                                        "Slide any task to the right to mark it as a favorite. Starred tasks are easier to spot and track."
                                    else "Slide a starred task to the left to remove it from favorites when it's no longer a priority.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )

                                Spacer(Modifier.height(32.dp))

                                SwipeAnimationPreview(
                                    isToFavorite = isToFavorite,
                                    isVisible = pagerState.currentPage == page
                                )
                            }
                            2 -> {
                                Text(
                                    text = "Let AI Polish Your Tasks",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Tap the AI icon to instantly fix grammar, spelling, or rewrite your tasks in a cleaner style.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )

                                Spacer(Modifier.height(24.dp))

                                AiRewriteAnimationPreview(
                                    isVisible = pagerState.currentPage == page
                                )
                            }
                            3 -> {
                                Text(
                                    text = "Choose Your AI Style",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Long-press the AI icon to switch between Standard, Professional, and Casual rewrite styles.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )

                                Spacer(Modifier.height(24.dp))

                                AiStyleAnimationPreview(
                                    isVisible = pagerState.currentPage == page
                                )
                            }
                            4 -> {
                                Text(
                                    text = "Step-by-Step Sorting",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Tap the arrows to move tasks up or down one step at a time, making it easy to prioritize your work.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )

                                Spacer(Modifier.height(24.dp))

                                TaskMovementAnimationPreview(
                                    isVisible = pagerState.currentPage == page
                                )
                            }
                            5 -> {
                                Text(
                                    text = "Fast Reordering",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Long-press the arrows to instantly move any task all the way to the absolute top or bottom of your list.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )

                                Spacer(Modifier.height(24.dp))

                                TaskLongPressMovementAnimationPreview(
                                    isVisible = pagerState.currentPage == page
                                )
                            }
                        }
                    }
                }

                // Stretching "Worm" Indicators
                Row(
                    Modifier
                        .height(24.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(6) { iteration ->
                        val isSelected = pagerState.currentPage == iteration
                        
                        val targetColor = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        
                        val dotColor by animateColorAsState(
                            targetValue = targetColor,
                            animationSpec = tween(150),
                            label = "dotColor"
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .layout { measurable, constraints ->
                                    val isCurrent = pagerState.currentPage == iteration
                                    val pageOffset = pagerState.currentPageOffsetFraction
                                    
                                    val stretchAmount = if (isCurrent) {
                                        (1f + (Math.abs(pageOffset) * 2.5f))
                                    } else if ((iteration == pagerState.currentPage + 1 && pageOffset > 0) || 
                                               (iteration == pagerState.currentPage - 1 && pageOffset < 0)) {
                                        (1f + ((1f - Math.abs(pageOffset)) * 2.5f))
                                    } else {
                                        1f
                                    }
                                    
                                    val widthDp = (10.dp * stretchAmount).coerceAtLeast(10.dp)
                                    val widthPx = widthDp.roundToPx()
                                    val heightPx = 8.dp.roundToPx()
                                    
                                    val placeable = measurable.measure(
                                        constraints.copy(
                                            minWidth = widthPx, 
                                            maxWidth = widthPx, 
                                            minHeight = heightPx, 
                                            maxHeight = heightPx
                                        )
                                    )
                                    layout(placeable.width, placeable.height) {
                                        placeable.placeRelative(0, 0)
                                    }
                                }
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Got it!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        }
    }
}

@Composable
fun TaskLongPressMovementAnimationPreview(isVisible: Boolean) {
    var progress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            val startTime = System.currentTimeMillis()
            val duration = 8000L
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                progress = (elapsed % duration) / duration.toFloat()
                kotlinx.coroutines.delay(16)
            }
        } else {
            progress = 0f
        }
    }

    // Timeline (0.0 to 1.0)
    // 0.0 - 0.1: Wait (0.8s)
    // 0.1 - 0.35: Finger on Task 1 Down (Pos 0) -> HOLD -> Move to Bottom (Pos 2)
    // 0.35 - 0.5: Wait (1.2s)
    // 0.5 - 0.75: Finger on Task 1 Up (Pos 2) -> HOLD -> Move back to Top (Pos 0)
    // 0.75 - 1.0: Final Wait (2s)

    val task1PosIndex = when {
        progress < 0.25f -> 0
        progress < 0.65f -> 2
        else -> 0
    }
    val task2PosIndex = when {
        progress < 0.25f -> 1
        progress < 0.65f -> 0
        else -> 1
    }
    val task3PosIndex = when {
        progress < 0.25f -> 2
        progress < 0.65f -> 1
        else -> 2
    }

    val t1Y by animateDpAsState(targetValue = (task1PosIndex * 60).dp, label = "lt1")
    val t2Y by animateDpAsState(targetValue = (task2PosIndex * 60).dp, label = "lt2")
    val t3Y by animateDpAsState(targetValue = (task3PosIndex * 60).dp, label = "lt3")

    val showFinger = progress in 0.1f..0.75f
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(modifier = Modifier.width(280.dp).height(180.dp)) {
            TaskMiniRow("Task 1", modifier = Modifier.offset(y = t1Y))
            TaskMiniRow("Task 2", modifier = Modifier.offset(y = t2Y))
            TaskMiniRow("Task 3", modifier = Modifier.offset(y = t3Y))

            if (showFinger) {
                val fingerScale by animateFloatAsState(
                    targetValue = when {
                        progress in 0.12f..0.22f -> 0.7f // Long press down hold
                        progress in 0.52f..0.62f -> 0.7f // Long press up hold
                        else -> 1f
                    },
                    label = "lScale"
                )

                val targetX = when {
                    progress < 0.45f -> 246f // Center on Down button
                    else -> 218f // Center on Up button
                }
                val targetY = when {
                    progress < 0.45f -> 13f // Task 1 at top
                    else -> 133f // Task 1 at bottom
                }

                val fx by animateDpAsState(targetX.dp, label = "lfx")
                val fy by animateDpAsState(targetY.dp, label = "lfy")

                Box(
                    modifier = Modifier
                        .offset(x = fx, y = fy)
                        .graphicsLayer(scaleX = fingerScale, scaleY = fingerScale)
                        .size(24.dp)
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                        .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                )
            }
        }
    }
}

@Composable
fun TaskMovementAnimationPreview(isVisible: Boolean) {
    var progress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            val startTime = System.currentTimeMillis()
            val duration = 10000L
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                progress = (elapsed % duration) / duration.toFloat()
                kotlinx.coroutines.delay(16)
            }
        } else {
            progress = 0f
        }
    }

    // Positions for 3 rows
    val rowHeight = 55.dp
    val spacing = 8.dp
    
    // Timeline (0.0 to 1.0)
    // 0.0 - 0.1: Wait (1s)
    // 0.1 - 0.2: Finger on Task 1 Down -> Move starts (1s)
    // 0.2 - 0.3: Wait (1s)
    // 0.3 - 0.4: Finger on Task 1 Down (at pos 1) -> Move starts (1s)
    // 0.4 - 0.5: Wait (1s)
    // 0.5 - 0.6: Finger on Task 1 Up (at pos 2) -> Move starts (1s)
    // 0.6 - 0.7: Wait (1s)
    // 0.7 - 0.8: Finger on Task 1 Up (at pos 1) -> Move starts (1s)
    // 0.8 - 1.0: Final Wait (2s)

    val task1PosIndex = when {
        progress < 0.15f -> 0
        progress < 0.35f -> 1
        progress < 0.55f -> 2
        progress < 0.75f -> 1
        else -> 0
    }
    
    val task2PosIndex = when {
        progress < 0.15f -> 1
        progress < 0.35f -> 0
        progress < 0.55f -> 0 // stays there while 1 moves to 2
        progress < 0.75f -> 0
        else -> 1
    }

    val task3PosIndex = when {
        progress < 0.35f -> 2
        progress < 0.55f -> 1
        progress < 0.75f -> 2
        else -> 2
    }

    val t1Y by animateDpAsState(targetValue = (task1PosIndex * 60).dp, label = "t1")
    val t2Y by animateDpAsState(targetValue = (task2PosIndex * 60).dp, label = "t2")
    val t3Y by animateDpAsState(targetValue = (task3PosIndex * 60).dp, label = "t3")

    val showFinger = progress in 0.1f..0.8f
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter
    ) {
        // ✅ Centered container for both rows and finger to ensure consistency across screens
        Box(modifier = Modifier.width(280.dp).height(180.dp)) {
            // Task 1
            TaskMiniRow("Task 1", modifier = Modifier.offset(y = t1Y))
            // Task 2
            TaskMiniRow("Task 2", modifier = Modifier.offset(y = t2Y))
            // Task 3
            TaskMiniRow("Task 3", modifier = Modifier.offset(y = t3Y))

            // --- FINGER (Now inside the centered 280dp box) ---
            if (showFinger) {
                val fingerSize = 24.dp
                val fingerScale by animateFloatAsState(
                    targetValue = if (progress in 0.14f..0.18f || progress in 0.34f..0.38f || 
                                      progress in 0.54f..0.58f || progress in 0.74f..0.78f) 0.8f else 1f,
                    label = "moveScale"
                )

                // targets calibrated relative to the 280dp box
                val targetX = when {
                    progress < 0.5f -> 246f // Center on Down button
                    else -> 218f // Center on Up button
                }

                val targetY = when {
                    progress < 0.25f -> 13f // Task 1 at Pos 0
                    progress < 0.45f -> 73f // Task 1 at Pos 1
                    progress < 0.65f -> 133f // Task 1 at Pos 2
                    else -> 73f // Task 1 at Pos 1
                }

                val fx by animateDpAsState(targetX.dp, label = "mfx")
                val fy by animateDpAsState(targetY.dp, label = "mfy")

                Box(
                    modifier = Modifier
                        .offset(x = fx, y = fy)
                        .graphicsLayer(scaleX = fingerScale, scaleY = fingerScale)
                        .size(fingerSize)
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                        .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun TaskMiniRow(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun AiStyleAnimationPreview(isVisible: Boolean) {
    var progress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            val startTime = System.currentTimeMillis()
            val duration = 10000L
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                progress = (elapsed % duration) / duration.toFloat()
                kotlinx.coroutines.delay(16)
            }
        } else {
            progress = 0f
        }
    }

    // Timeline (0.0 to 1.0)
    // 0.0 - 0.10: Wait 1s
    // 0.10 - 0.22: Finger on AI (Long Press starts)
    // 0.22 - 0.33: Dialog appears (Finger still on AI)
    // 0.33 - 0.44: Finger moves to Professional
    // 0.44 - 0.50: Tap Professional
    // 0.50 - 0.55: Dialog closes
    // 0.55 - 0.66: Finger moves back to AI
    // 0.66 - 0.72: Tap AI
    // 0.72 - 0.88: Loader
    // 0.88 - 1.0: Final result wait (1.2s)

    val showDialog = progress in 0.22f..0.52f
    val isProfessionalSelected = progress > 0.48f
    val showLoader = progress in 0.72f..0.88f
    val showResult = progress > 0.88f
    val showFinger = progress in 0.10f..0.72f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp) // standardized height
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        // --- TASK CARD (Centered) ---
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(70.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color.LightGray.copy(alpha = 0.4f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                Modifier.fillMaxSize().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (showLoader) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (showResult) "Complete report quickly." else "Finsh reprt fast",
                        fontSize = 14.sp,
                        fontWeight = if (showResult) FontWeight.Bold else FontWeight.Normal,
                        color = if (showResult) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // --- STYLE DIALOG (Positioned ABOVE the centered card) ---
        if (showDialog) {
            Card(
                modifier = Modifier
                    .width(150.dp) // Adjusted width
                    .offset(y = (-5).dp), // Moved slightly lower
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text("AI Style", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
                    listOf("Standard", "Professional", "Casual").forEach { style ->
                        val isThisSelected = if (style == "Professional") isProfessionalSelected else (style == "Standard" && !isProfessionalSelected)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isThisSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                .padding(vertical = 10.dp, horizontal = 10.dp) // Increased vertical padding to increase length
                        ) {
                            Text(style, fontSize = 12.sp, color = if (isThisSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // --- FINGER ---
        if (showFinger) {
            val fingerScale by animateFloatAsState(
                targetValue = when {
                    progress in 0.15f..0.33f -> 0.7f 
                    progress in 0.44f..0.50f -> 0.8f 
                    progress in 0.68f..0.75f -> 0.8f 
                    else -> 1f
                },
                label = "fScale"
            )

            // targets aligned with centered layout
            val fingerTargetX = when {
                progress < 0.33f -> 32f 
                progress < 0.55f -> 120f // Calibrated for 150dp width
                else -> 32f 
            }

            val fingerTargetY = when {
                progress < 0.33f -> 0f // Hit AI button on centered card
                progress < 0.55f -> 12f // Hit Professional in centered dialog (moved from 0f)
                else -> 0f // Back on AI button
            }

            val fx by animateDpAsState(fingerTargetX.dp, label = "fx")
            val fy by animateDpAsState(fingerTargetY.dp, label = "fy")

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = fx, y = fy)
                    .graphicsLayer(scaleX = fingerScale, scaleY = fingerScale)
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.8f), CircleShape)
                    .border(1.5.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

@Composable
fun AiRewriteAnimationPreview(isVisible: Boolean) {
    var progress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            val startTime = System.currentTimeMillis()
            val duration = 7000L
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                progress = (elapsed % duration) / duration.toFloat()
                kotlinx.coroutines.delay(16)
            }
        } else {
            progress = 0f
        }
    }

    val fullBrokenText = "By mil and bred"
    val fullCorrectText = "Buy milk and bread"

    val typingProgress = (progress / 0.4f).coerceIn(0f, 1f)
    val charactersToShow = (typingProgress * fullBrokenText.length).roundToInt()
    val currentBrokenText = fullBrokenText.take(charactersToShow)
    
    val isAiActive = charactersToShow >= 7
    val showLoader = progress in 0.65f..0.85f
    val showFinalResult = progress > 0.85f
    val showFinger = progress in 0.53f..0.67f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp) // Standardized height
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(70.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color.LightGray.copy(alpha = 0.4f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                Modifier.fillMaxSize().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (showLoader) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        val iconColor = when {
                            showFinalResult || (isAiActive && progress > 0.4f) -> MaterialTheme.colorScheme.primary
                            isAiActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            else -> Color.Gray.copy(alpha = 0.3f)
                        }
                        Icon(imageVector = Icons.Default.AutoAwesome, null, tint = iconColor, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (showFinalResult) fullCorrectText else currentBrokenText,
                        fontSize = 14.sp,
                        fontWeight = if (showFinalResult) FontWeight.Bold else FontWeight.Normal,
                        color = if (showFinalResult) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.8f)
                    )
                }
            }
        }

        if (showFinger) {
            val fingerScale by animateFloatAsState(targetValue = if (progress in 0.62f..0.66f) 0.8f else 1f, label = "fingerScale")
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp)
                    .graphicsLayer(scaleX = fingerScale, scaleY = fingerScale)
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.8f), CircleShape)
                    .border(1.5.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

@Composable
fun SwipeAnimationPreview(isToFavorite: Boolean, isVisible: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "tutorial")
    val gold = Color(0xFFFFD700)
    val gray = Color.LightGray
    val startX = 0f
    val targetX = if (isToFavorite) 140f else -140f
    val xOffset by if (isVisible) {
        infiniteTransition.animateFloat(
            initialValue = startX,
            targetValue = targetX,
            animationSpec = infiniteRepeatable(animation = tween(2500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Restart),
            label = "offset"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    val isPastThreshold = if (isToFavorite) xOffset > 80f else xOffset < -80f
    val targetBorderColor = if (isToFavorite) { if (isPastThreshold) gold else gray.copy(alpha = 0.4f) } else { if (isPastThreshold) gray.copy(alpha = 0.4f) else gold }
    val borderColor by animateColorAsState(targetValue = targetBorderColor, label = "border")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp) // Standardized height
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(0.9f).height(70.dp).clip(RoundedCornerShape(16.dp)).background(if (isToFavorite) gold.copy(alpha = 0.15f) else gray.copy(alpha = 0.15f)).padding(horizontal = 20.dp),
            contentAlignment = if (isToFavorite) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            if (isToFavorite) Icon(Icons.Default.Star, null, tint = gold) else Icon(Icons.Default.StarOutline, null, tint = Color.Gray)
        }
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).height(70.dp).offset { IntOffset(xOffset.roundToInt(), 0) },
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, borderColor),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val showCardStar = if (isToFavorite) isPastThreshold else !isPastThreshold
                if (showCardStar) { Icon(Icons.Default.Star, null, tint = gold, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
                Box(Modifier.height(10.dp).fillMaxWidth(0.6f).background(gray.copy(alpha = 0.3f), CircleShape))
            }
        }
        Box(modifier = Modifier.offset { IntOffset(xOffset.roundToInt(), 35) }.size(34.dp).background(Color.White.copy(alpha = 0.8f), CircleShape).border(1.5.dp, Color.Gray.copy(alpha = 0.5f), CircleShape))
    }
}