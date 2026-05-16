package com.example.mytodoapp.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytodoapp.R
import com.example.mytodoapp.auth.AuthManager
import com.example.mytodoapp.sync.SyncManager
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import com.example.mytodoapp.ui.theme.MyTodoAppTheme

@Composable
fun LoginScreen(
    authManager: AuthManager,
    syncManager: SyncManager,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    // Subtle pulse animation for the app icon
    var iconAnimTrigger by remember { mutableStateOf(false) }
    val iconScale by animateFloatAsState(
        targetValue = if (iconAnimTrigger) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 1200),
        label = "iconPulse",
        finishedListener = { iconAnimTrigger = !iconAnimTrigger }
    )
    LaunchedEffect(Unit) { iconAnimTrigger = true }

    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
        ) {

            // ── App Icon ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .scale(iconScale)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.18f),
                                primary.copy(alpha = 0.06f)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.5f),
                                primary.copy(alpha = 0.15f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "App Icon",
                    modifier = Modifier.size(120.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── App Name ──────────────────────────────────────────
            Text(
                text = "TodoApp",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Tagline ───────────────────────────────────────────
            Text(
                text = "Stay organized, stay ahead.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(52.dp))

            // ── Buttons ───────────────────────────────────────────
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            } else {

                // Google Sign-In Button
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            val result = authManager.signInWithGoogle(context)
                            if (result.isSuccess) {
                                syncManager.waitForInitialSettings()
                                onLoginSuccess()
                            } else {
                                isLoading = false
                                Toast.makeText(
                                    context,
                                    "Sign in failed: ${result.exceptionOrNull()?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google),
                        contentDescription = "Google",
                        modifier = Modifier.size(20.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Continue with Google",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Guest Button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            val result = authManager.signInAnonymously()
                            isLoading = false
                            if (result.isSuccess) {
                                onLoginSuccess()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Guest login failed: ${result.exceptionOrNull()?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Guest",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Continue as Guest",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp
                    )
                }
            }
        }
    }
}
