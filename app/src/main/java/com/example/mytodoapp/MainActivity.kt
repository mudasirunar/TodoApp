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
import com.example.mytodoapp.ui.theme.MyTodoAppTheme
import com.example.mytodoapp.ui.viewmodel.TodoViewModel
import com.example.mytodoapp.ui.viewmodel.TodoViewModelFactory
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.mytodoapp.ui.screens.SettingsScreen
import com.example.mytodoapp.utils.ThemeMode
import com.example.mytodoapp.utils.PreferenceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers

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

            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> {
                    val systemConfig = android.content.res.Resources.getSystem().configuration
                    (systemConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
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
                                slideOutHorizontally(animationSpec = tween(400)) { -it } + fadeOut(tween(400)) 
                            },
                            popEnterTransition = { 
                                slideInHorizontally(animationSpec = tween(400)) { -it } + fadeIn(tween(400)) 
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
                                
                                SettingsScreen(
                                    currentTheme = currentTheme ?: ThemeMode.SYSTEM,
                                    onThemeSelected = { viewModel.saveThemeMode(it) },
                                    currentAiStyle = currentAiStyle ?: com.example.mytodoapp.utils.RewriteType.Standard,
                                    onAiStyleSelected = { viewModel.saveAiRewriteType(it) },
                                    onBack = { navController.popBackStack() }
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
                                    }
                                )
                            }
                    }
                }
            }
        }
    }
}
