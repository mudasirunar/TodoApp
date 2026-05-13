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

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val preferenceManager = PreferenceManager(this)
        
        // ✅ SPLASH SCREEN: Wait for the theme to be loaded
        val splashScreen = installSplashScreen()
        var isThemeReady by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !isThemeReady }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = TodoDatabase.getDatabase(this)
        val todoDao = database.todoDao()

        setContent {
            val viewModel: TodoViewModel = viewModel(
                factory = TodoViewModelFactory(todoDao, preferenceManager)
            )

            // ✅ OPTIMIZATION: Collect from ViewModel (Optimistic UI) instead of DataStore directly
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            
            LaunchedEffect(themeMode) {
                if (themeMode != null) {
                    isThemeReady = true
                }
            }

            // Fallback while loading
            if (themeMode == null) return@setContent

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

                        NavHost(
                            navController,
                            startDestination = "dashboard",
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
                            composable("dashboard") { backStackEntry ->
                                val groups by viewModel.groups.collectAsStateWithLifecycle()
                                val softDeleteGroupId by backStackEntry.savedStateHandle.getStateFlow<String?>(
                                    "soft_delete_group_id",
                                    null
                                ).collectAsStateWithLifecycle()

                                DashboardScreen(
                                    groups = groups,
                                    softDeleteGroupId = softDeleteGroupId,
                                    onSoftDeleteHandled = {
                                        backStackEntry.savedStateHandle.remove<String>("soft_delete_group_id")
                                    },
                                    onNavigateToEdit = { group, searchQuery ->
                                        navController.navigate("edit/${group.id}?query=$searchQuery")
                                    },
                                    onNavigateToSettings = {
                                        navController.navigate("settings")
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
                                    onBack = { navController.popBackStack() }
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
                                val groups by viewModel.groups.collectAsStateWithLifecycle()
                                val previewGroup by viewModel.previewGroup.collectAsStateWithLifecycle()
                                val pdfConfig by viewModel.pdfConfig.collectAsStateWithLifecycle()

                                // Use previewGroup if available (unsaved data), otherwise fallback to DB
                                val group = previewGroup ?: groups?.find { it.id == groupId } ?: TodoGroup(id = groupId)

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
                                val groups by viewModel.groups.collectAsStateWithLifecycle()
                                if (groups == null) {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = androidx.compose.ui.Alignment.Center
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator()
                                    }
                                    return@composable
                                }
                                val id = backStack.arguments?.getString("groupId") ?: ""
                                val query = backStack.arguments?.getString("query") ?: ""
                                val existing = groups?.find { it.id == id } ?: TodoGroup(id = id)

                                AddTodoScreen(
                                    existingGroup = existing,
                                    highlightQuery = query,
                                    viewModel = viewModel,
                                    onSave = { updated ->
                                        viewModel.insertGroup(updated)
                                        viewModel.clearHistory()
                                        navController.popBackStack()
                                    },
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
                                        // ✅ SHARE VIA VIEWMODEL
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
