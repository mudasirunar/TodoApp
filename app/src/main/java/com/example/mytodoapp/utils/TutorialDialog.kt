package com.example.mytodoapp.utils

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

@Composable
fun SwipeTutorialDialog(onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ✅ Updated Main Heading
                Text(
                    "Quick Guide",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(20.dp))

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                ) { page ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val isToFavorite = page == 0

                        // ✅ Improved Messaging for each slide
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
                            isVisible = pagerState.currentPage == page // Only animate if active
                        )
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
                    repeat(2) { iteration ->
                        val isSelected = pagerState.currentPage == iteration
                        // pageOffset goes from 0.0 to 1.0 as you swipe
                        val pageOffset = pagerState.currentPageOffsetFraction

                        // This calculates a "stretch factor" based on the swipe progress
                        val stretchAmount = if (isSelected) {
                            // As we move away from this dot, it grows
                            (1f + (Math.abs(pageOffset) * 2.5f))
                        } else if ((iteration == 1 && pageOffset < 0) || (iteration == 0 && pageOffset > 0)) {
                            // As we move toward this dot, it also grows to "meet" the other one
                            (1f + ((1f - Math.abs(pageOffset)) * 2.5f))
                        } else {
                            1f
                        }

                        val dotWidth by animateDpAsState(
                            targetValue = (10 * stretchAmount).dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "wormWidth"
                        )

                        val dotColor = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                // We keep height at 8dp but let width expand significantly
                                .size(width = dotWidth.coerceAtLeast(10.dp), height = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Got it!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
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
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "offset"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val isPastThreshold = if (isToFavorite) xOffset > 80f else xOffset < -80f

    val targetBorderColor = if (isToFavorite) {
        if (isPastThreshold) gold else gray.copy(alpha = 0.4f)
    } else {
        if (isPastThreshold) gray.copy(alpha = 0.4f) else gold
    }
    val borderColor by animateColorAsState(targetValue = targetBorderColor, label = "border")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp) // Height enough to fit the finger shadow
            .clipToBounds(), // ⬅️ THIS prevents the card/finger from entering other slides
        contentAlignment = Alignment.Center
    ) {
        // --- BACKGROUND LAYER (REVEAL) ---
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(70.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isToFavorite) gold.copy(alpha = 0.15f)
                    else gray.copy(alpha = 0.15f)
                )
                .padding(horizontal = 20.dp),
            contentAlignment = if (isToFavorite) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            if (isToFavorite) {
                Icon(Icons.Default.Star, null, tint = gold)
            } else {
                Icon(Icons.Default.StarOutline, null, tint = Color.Gray)
            }
        }

        // --- SLIDING CARD ---
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(70.dp)
                .offset { IntOffset(xOffset.roundToInt(), 0) },
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, borderColor),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                Modifier.fillMaxSize().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val showCardStar = if (isToFavorite) isPastThreshold else !isPastThreshold
                if (showCardStar) {
                    Icon(Icons.Default.Star, null, tint = gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.height(10.dp).fillMaxWidth(0.6f).background(gray.copy(alpha = 0.3f), CircleShape))
            }
        }

        // --- FINGER ---
        Box(
            modifier = Modifier
                .offset { IntOffset(xOffset.roundToInt(), 35) }
                .size(34.dp)
                .background(Color.White.copy(alpha = 0.8f), CircleShape)
                .border(1.5.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
        )
    }
}