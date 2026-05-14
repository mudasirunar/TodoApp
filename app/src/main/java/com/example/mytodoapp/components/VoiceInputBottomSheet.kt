package com.example.mytodoapp.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytodoapp.ui.viewmodel.SpeechUiState

import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInputBottomSheet(
    state: SpeechUiState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onPauseListening: () -> Unit,
    onResumeListening: () -> Unit,
    onCancel: () -> Unit,
    onRewriteWithAi: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Custom NestedScrollConnection to prevent the bottom sheet from hijacking the scroll
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                // Return Offset.Zero to allow the child (scrollable text) to take what it needs first
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                // Consume all remaining scroll to prevent it from reaching the BottomSheet
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Consume all remaining velocity to prevent it from reaching the BottomSheet
                return available
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP SECTION: Animated Mic
            Box(
                modifier = Modifier
                    .size(140.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.isListening) {
                    WaveformAnimation()
                }
                
                Surface(
                    onClick = {
                        if (state.isListening) onStopListening() else if (!state.isProcessingAi) onStartListening()
                    },
                    modifier = Modifier.size(90.dp),
                    shape = CircleShape,
                    color = if (state.isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = if (state.isListening) 12.dp else 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.isListening) Icons.Default.Mic else Icons.Default.MicNone,
                            contentDescription = "Mic",
                            modifier = Modifier.size(44.dp),
                            tint = if (state.isListening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = when {
                    state.isProcessingAi -> "AI is polishing your text..."
                    state.isListening -> "Listening..."
                    state.isPaused -> "Paused"
                    state.finalTranscript.isNotEmpty() -> "Review Transcript"
                    else -> "Tap mic to start"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // MIDDLE SECTION: Live Transcription
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 240.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = when {
                    state.error != null -> BorderStroke(2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    state.isListening -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                }
            ) {
                Box(
                    modifier = Modifier
                        .padding(20.dp)
                        .nestedScroll(nestedScrollConnection)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.error != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.error,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        val displayText = buildString {
                            if (state.finalTranscript.isNotEmpty()) {
                                append(state.finalTranscript)
                            }
                            if (state.partialTranscript.isNotEmpty()) {
                                if (this.isNotEmpty()) append(" ")
                                append(state.partialTranscript)
                            }
                        }

                        if (displayText.isEmpty()) {
                            Text(
                                text = "Your speech will appear here...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            Text(
                                text = displayText,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 30.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // BOTTOM ACTIONS
            if (state.isListening || state.isPaused) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(60.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    ) {
                        Icon(
                            Icons.Default.Close, 
                            contentDescription = "Cancel", 
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (state.isListening) {
                        Button(
                            onClick = onStopListening,
                            modifier = Modifier.height(60.dp).weight(1f).padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop Recording", fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = onPauseListening,
                            modifier = Modifier
                                .size(60.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        ) {
                            Icon(
                                Icons.Default.Pause, 
                                contentDescription = "Pause", 
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Button(
                            onClick = onResumeListening,
                            modifier = Modifier.height(60.dp).weight(1f).padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resume", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (state.finalTranscript.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onStartListening,
                            modifier = Modifier.height(56.dp).weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Record Again", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onConfirm(state.finalTranscript) },
                            modifier = Modifier.height(56.dp).weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use Text", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { onRewriteWithAi(state.finalTranscript) },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        enabled = !state.isProcessingAi
                    ) {
                        if (state.isProcessingAi) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Refine with AI", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
 else {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WaveformAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale1"
    )
    
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale2"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer(scaleX = scale1, scaleY = scale1)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer(scaleX = scale2, scaleY = scale2)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape)
        )
    }
}
