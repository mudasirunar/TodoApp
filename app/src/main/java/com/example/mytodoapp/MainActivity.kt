package com.example.mytodoapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.mytodoapp.data.TodoDatabase
import com.example.mytodoapp.data.TodoGroup
import com.example.mytodoapp.ui.screens.AddTodoScreen
import com.example.mytodoapp.ui.screens.DashboardScreen
import com.example.mytodoapp.ui.screens.PdfPreviewScreen
import com.example.mytodoapp.ui.theme.MyTodoAppTheme
import com.example.mytodoapp.ui.viewmodel.TodoViewModel
import com.example.mytodoapp.ui.viewmodel.TodoViewModelFactory
import androidx.appcompat.app.AppCompatActivity
import com.example.mytodoapp.components.RewriteType
import com.example.mytodoapp.ui.screens.SettingsScreen
import com.example.mytodoapp.utils.ThemeMode
import com.example.mytodoapp.utils.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import com.example.mytodoapp.sync.SyncManager
import com.example.mytodoapp.auth.AuthManager
import com.example.mytodoapp.ui.screens.LoginScreen

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        var isThemeReady by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !isThemeReady }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = TodoDatabase.getDatabase(this)
        val todoDao = database.todoDao()
        val preferenceManager = PreferenceManager.getInstance(this)
        val syncManager = SyncManager(this, preferenceManager)
        val authManager = AuthManager(this.applicationContext, syncManager, preferenceManager)

        setContent {
            val viewModel: TodoViewModel = viewModel(
                factory = TodoViewModelFactory(todoDao, preferenceManager, syncManager)
            )

            // ✅ OPTIMIZATION: Collect from ViewModel (Optimistic UI) instead of DataStore directly
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            LaunchedEffect(themeMode) {
                isThemeReady = true
            }

            // ✅ AUTH-BASED SYNC INITIALIZATION
            val authState by authManager.authState.collectAsState()

            LaunchedEffect(authState) {
                if (authState == com.example.mytodoapp.auth.AuthState.AUTHENTICATED ||
                    authState == com.example.mytodoapp.auth.AuthState.GUEST) {

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

            MyTodoAppTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val startDestination = if (authManager.currentUser == null) "login" else "dashboard"

                    NavHost(
                        navController,
                        startDestination = startDestination,
                        enterTransition = {
                            slideInHorizontally(animationSpec = tween(400)) { it } + fadeIn(tween(400))
                        },
                        exitTransition = {
                            if (targetState.destination.route?.startsWith("pdf_preview") == true) {
                                fadeOut(tween(400))
                            } else {
                                slideOutHorizontally(animationSpec = tween(400)) { -it } + fadeOut(tween(400))
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route?.startsWith("pdf_preview") == true) {
                                fadeIn(tween(400))
                            } else {
                                slideInHorizontally(animationSpec = tween(400)) { -it } + fadeIn(tween(400))
                            }
                        },
                        popExitTransition = {
                            slideOutHorizontally(animationSpec = tween(400)) { it } + fadeOut(tween(400))
                        }
                    ) {
                        composable("login") {
                            LoginScreen(
                                authManager = authManager,
                                syncManager = syncManager,
                                onLoginSuccess = {
                                    navController.navigate("dashboard") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("dashboard") { backStackEntry ->
                            val groups by viewModel.activeGroups.collectAsStateWithLifecycle()
                            val importState by viewModel.importState.collectAsStateWithLifecycle()
                            val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
                            val softDeleteGroupId by backStackEntry.savedStateHandle.getStateFlow<String?>(
                                "soft_delete_group_id",
                                null
                            ).collectAsStateWithLifecycle()

                            DashboardScreen(
                                groups = groups,
                                importState = importState,
                                onResetImportState = { viewModel.resetImportState() },
                                softDeleteGroupId = softDeleteGroupId,
                                onSoftDeleteHandled = {
                                    backStackEntry.savedStateHandle.remove<String>("soft_delete_group_id")
                                },
                                authManager = authManager,
                                isSyncing = isSyncing,
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
                                currentPdfConfig = currentPdfConfig ?: com.example.mytodoapp.utils.PdfConfig(),
                                onPdfConfigChange = { viewModel.savePdfConfig(it) },
                                moveDoneToBottom = moveDoneToBottom ?: false,
                                onMoveDoneToBottomChange = { viewModel.saveMoveDoneToBottom(it) },
                                viewModel = viewModel,
                                authManager = authManager,
                                syncManager = syncManager,
                                onBack = { navController.popBackStack() },
                                onNavigateToDashboard = {
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
                                config = pdfConfig ?: com.example.mytodoapp.utils.PdfConfig(),
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