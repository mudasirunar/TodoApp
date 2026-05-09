package com.example.mytodoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = TodoDatabase.getDatabase(this)
        val todoDao = database.todoDao()

        setContent {
            val viewModel: TodoViewModel = viewModel(
                factory = TodoViewModelFactory(todoDao)
            )

            // USE YOUR CUSTOM THEME HERE
            MyTodoAppTheme {
                // Surface ensures the background color is applied correctly across the whole screen
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController,
                        startDestination = "dashboard",
                        enterTransition = { fadeIn(tween(300)) },
                        exitTransition = { fadeOut(tween(300)) },
                        popEnterTransition = { fadeIn(tween(300)) },
                        popExitTransition = { fadeOut(tween(300)) }
                    ) {
                        composable("dashboard") { backStackEntry ->
                            val groups by viewModel.groups.collectAsStateWithLifecycle()
                            val softDeleteGroupId by backStackEntry.savedStateHandle.getStateFlow<String?>("soft_delete_group_id", null).collectAsStateWithLifecycle()
                            
                            DashboardScreen(
                                groups = groups,
                                softDeleteGroupId = softDeleteGroupId,
                                onSoftDeleteHandled = {
                                    backStackEntry.savedStateHandle.remove<String>("soft_delete_group_id")
                                },
                                onNavigateToEdit = { group, searchQuery ->
                                    navController.navigate("edit/${group.id}?query=$searchQuery")
                                },
                                onDeleteGroup = { group ->
                                    viewModel.deleteGroup(group)
                                },
                                onTogglePin = { group ->
                                    viewModel.togglePin(group)
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
                                    navController.previousBackStackEntry?.savedStateHandle?.set("soft_delete_group_id", existing.id)
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