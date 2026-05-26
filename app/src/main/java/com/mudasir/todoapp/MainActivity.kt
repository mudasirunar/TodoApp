package com.mudasir.todoapp

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mudasir.todoapp.auth.AuthManager
import com.mudasir.todoapp.components.RewriteType
import com.mudasir.todoapp.data.TodoDatabase
import com.mudasir.todoapp.data.TodoGroup
import com.mudasir.todoapp.sync.SyncManager
import com.mudasir.todoapp.ui.screens.AddTodoScreen
import com.mudasir.todoapp.ui.screens.DashboardScreen
import com.mudasir.todoapp.ui.screens.LoginScreen
import com.mudasir.todoapp.ui.screens.PdfPreviewScreen
import com.mudasir.todoapp.ui.screens.SettingsScreen
import com.mudasir.todoapp.ui.theme.ToDoAppTheme
import com.mudasir.todoapp.ui.viewmodel.TodoViewModel
import com.mudasir.todoapp.ui.viewmodel.TodoViewModelFactory
import com.mudasir.todoapp.utils.PreferenceManager
import com.mudasir.todoapp.utils.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        com.mudasir.todoapp.utils.AnalyticsManager.init(this)
        
        installSplashScreen()
        
        val preferenceManager = PreferenceManager.getInstance(this)
        
        val initialThemeMode = runBlocking { preferenceManager.themeMode.first() }
        val initialIsOfflineGuest = runBlocking { preferenceManager.isOfflineGuest.first() }
        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isInitiallyDark = when (initialThemeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemDark
        }

        // Apply edge-to-edge immediately with the correct pre-calculated style
        val initialStyle = if (isInitiallyDark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = initialStyle, navigationBarStyle = initialStyle)

        super.onCreate(savedInstanceState)

        val database = TodoDatabase.getDatabase(this)
        val todoDao = database.todoDao()
        val syncManager = SyncManager(this, preferenceManager)
        val authManager = AuthManager(this.applicationContext, syncManager, preferenceManager)

        setContent {
            val viewModel: TodoViewModel = viewModel(
                factory = TodoViewModelFactory(todoDao, preferenceManager, syncManager)
            )

            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle(initialValue = initialThemeMode)
            val isOfflineGuest by preferenceManager.isOfflineGuest.collectAsState(initial = initialIsOfflineGuest)

            // AUTH-BASED SYNC INITIALIZATION
            val authState by authManager.authState.collectAsState()

            LaunchedEffect(authState) {
                if (authState == com.mudasir.todoapp.auth.AuthState.AUTHENTICATED ||
                    authState == com.mudasir.todoapp.auth.AuthState.GUEST) {

                    if (authState == com.mudasir.todoapp.auth.AuthState.GUEST && authManager.currentUser == null) {
                        if (com.mudasir.todoapp.utils.NetworkUtils.isNetworkAvailable(this@MainActivity)) {
                            val result = authManager.signInAnonymously()
                            if (result.isSuccess) {
                                authManager.setOfflineGuest(false)
                            }
                        }
                    }

                    val hasMigrated = preferenceManager.hasMigratedToCloud.first()
                    if (!hasMigrated) {
                        syncManager.migrateLocalDataToCloud()
                        preferenceManager.markMigratedToCloud()
                    }
                    syncManager.startRealtimeSync()
                } else {
                    syncManager.stopRealtimeSync()
                }
            }

            val systemTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> systemTheme
            }

            LaunchedEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            ToDoAppTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val startDestination = if (authManager.currentUser == null && !isOfflineGuest) "login" else "dashboard"

                    NavHost(
                        navController,
                        startDestination = startDestination,
                        enterTransition = {
                            slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(tween(300))
                        },
                        exitTransition = {
                            if (targetState.destination.route?.startsWith("pdf_preview") == true) {
                                fadeOut(tween(300))
                            } else {
                                slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut(tween(300))
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route?.startsWith("pdf_preview") == true) {
                                fadeIn(tween(300))
                            } else {
                                slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn(tween(300))
                            }
                        },
                        popExitTransition = {
                            slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut(tween(300))
                        }
                    ) {
                        composable("login") {
                            LoginScreen(
                                authManager = authManager,
                                syncManager = syncManager,
                                onLoginSuccess = {
                                    viewModel.triggerScrollToTop()
                                    navController.navigate("dashboard") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("dashboard") { backStackEntry ->
                            val groups by viewModel.activeGroups.collectAsStateWithLifecycle()
                            val importState by viewModel.importState.collectAsStateWithLifecycle()
                            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
                            val shouldScrollToTop by viewModel.shouldScrollToTop.collectAsStateWithLifecycle()
                            val softDeleteGroupId by backStackEntry.savedStateHandle.getStateFlow<String?>(
                                "soft_delete_group_id",
                                null
                            ).collectAsStateWithLifecycle()

                            DashboardScreen(
                                groups = groups,
                                importState = importState,
                                isLoading = isLoading,
                                shouldScrollToTop = shouldScrollToTop,
                                onScrollToTopHandled = { viewModel.clearScrollToTop() },
                                onResetImportState = { viewModel.resetImportState() },
                                softDeleteGroupId = softDeleteGroupId,
                                onSoftDeleteHandled = {
                                    backStackEntry.savedStateHandle.remove<String>("soft_delete_group_id")
                                },
                                authManager = authManager,
                                onNavigateToEdit = { group, searchQuery ->
                                    navController.navigate("edit/${group.id}?query=$searchQuery") {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings") {
                                        launchSingleTop = true
                                    }
                                },
                                onDeleteGroup = { group ->
                                    viewModel.deleteGroup(group)
                                },
                                onTogglePin = { group ->
                                    viewModel.togglePin(group)
                                }
                            )
                        }

                        composable("settings") {
                            val currentTheme by viewModel.themeMode.collectAsStateWithLifecycle()
                            val currentAiStyle by viewModel.aiRewriteType.collectAsStateWithLifecycle()
                            val currentPdfConfig by viewModel.pdfConfig.collectAsStateWithLifecycle()
                            val moveDoneToBottom by viewModel.moveDoneToBottom.collectAsStateWithLifecycle()
                            SettingsScreen(
                                currentTheme = currentTheme ?: ThemeMode.SYSTEM,
                                onThemeSelected = { viewModel.saveThemeMode(it) },
                                currentAiStyle = currentAiStyle ?: RewriteType.Standard,
                                onAiStyleSelected = { viewModel.saveAiRewriteType(it) },
                                currentPdfConfig = currentPdfConfig ?: com.mudasir.todoapp.utils.PdfConfig(),
                                onPdfConfigChange = { viewModel.savePdfConfig(it) },
                                moveDoneToBottom = moveDoneToBottom ?: false,
                                onMoveDoneToBottomChange = { viewModel.saveMoveDoneToBottom(it) },
                                viewModel = viewModel,
                                authManager = authManager,
                                syncManager = syncManager,
                                onBack = { navController.popBackStack() },
                                onNavigateToDashboard = {
                                    viewModel.triggerScrollToTop()
                                    navController.navigate("dashboard") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onNavigateToLogin = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = "pdf_preview/{groupId}",
                            arguments = listOf(
                                navArgument("groupId") { type = NavType.StringType }
                            ),
                            enterTransition = {
                                slideInVertically(animationSpec = tween(400)) { it } + fadeIn(tween(400))
                            },
                            exitTransition = {
                                slideOutVertically(animationSpec = tween(400)) { -it } + fadeOut(tween(400))
                            },
                            popEnterTransition = {
                                slideInVertically(animationSpec = tween(400)) { -it } + fadeIn(tween(400))
                            },
                            popExitTransition = {
                                slideOutVertically(animationSpec = tween(400)) { it } + fadeOut(tween(400))
                            }
                        ) { backStack ->
                            val groupId = backStack.arguments?.getString("groupId") ?: ""
                            val groups by viewModel.allGroups.collectAsStateWithLifecycle()
                            val previewGroup by viewModel.previewGroup.collectAsStateWithLifecycle()
                            val pdfConfig by viewModel.pdfConfig.collectAsStateWithLifecycle()

                            // Use previewGroup if available (unsaved data), otherwise fallback to DB
                            val group = previewGroup ?: groups.find { it.id == groupId } ?: TodoGroup(id = groupId)

                            PdfPreviewScreen(
                                group = group,
                                config = pdfConfig ?: com.mudasir.todoapp.utils.PdfConfig(),
                                onBack = {
                                    viewModel.setPreviewGroup(null) // Clean up
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(
                            route = "edit/{groupId}?query={query}",
                            arguments = listOf(
                                navArgument("groupId") { type = NavType.StringType },
                                navArgument("query") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = ""
                                }
                            )
                        ) { backStack ->
                            val groups by viewModel.allGroups.collectAsStateWithLifecycle()
                            val id = backStack.arguments?.getString("groupId") ?: ""
                            val query = backStack.arguments?.getString("query") ?: ""
                            val existing = groups.find { it.id == id } ?: TodoGroup(id = id)
                            AddTodoScreen(
                                existingGroup = existing,
                                highlightQuery = query,
                                viewModel = viewModel,
                                onBack = {
                                    viewModel.clearHistory()
                                    navController.popBackStack()
                                },
                                onDelete = {
                                    viewModel.clearHistory()
                                    navController.previousBackStackEntry?.savedStateHandle?.set(
                                        "soft_delete_group_id",
                                        existing.id
                                    )
                                    navController.popBackStack()
                                },
                                onNavigateToPreview = { unsavedGroup ->
                                    viewModel.forceImmediateSave()
                                    viewModel.setPreviewGroup(unsavedGroup)
                                    navController.navigate("pdf_preview/${unsavedGroup.id}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}