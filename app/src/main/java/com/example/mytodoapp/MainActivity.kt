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
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.mytodoapp.data.TodoDatabase
import com.example.mytodoapp.data.TodoGroup
import com.example.mytodoapp.data.TodoGroupEntity
import com.example.mytodoapp.ui.screens.AddTodoScreen
import com.example.mytodoapp.ui.screens.DashboardScreen
import com.example.mytodoapp.ui.theme.MyTodoAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = TodoDatabase.getDatabase(this)
        val todoDao = database.todoDao()

        setContent {
            // Observe Room Database Flow
            val groupsEntity by todoDao.getAllGroups().collectAsState(initial = emptyList())
            val groups = groupsEntity.map {
                TodoGroup(
                    id = it.id,
                    title = it.title,
                    tasks = it.tasks,
                    createdAt = it.createdAt,
                    isPinned = it.isPinned
                )
            }

            // USE YOUR CUSTOM THEME HERE
            MyTodoAppTheme {
                // Surface ensures the background color is applied correctly across the whole screen
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val scope = rememberCoroutineScope()

                    NavHost(
                        navController,
                        startDestination = "dashboard",
                        enterTransition = { fadeIn(tween(300)) },
                        exitTransition = { fadeOut(tween(300)) },
                        popEnterTransition = { fadeIn(tween(300)) },
                        popExitTransition = { fadeOut(tween(300)) }
                    ) {
                        composable("dashboard") {
                            // 1. Ensure your mapping includes isPinned
                            val groups = groupsEntity.map {
                                TodoGroup(
                                    it.id,
                                    it.title,
                                    it.tasks,
                                    it.createdAt,
                                    it.isPinned
                                ) // Added isPinned here
                            }

                            DashboardScreen(
                                groups = groups,
                                onNavigateToEdit = { group, searchQuery ->
                                    navController.navigate("edit/${group.id}?query=$searchQuery")
                                },
                                onDeleteGroup = { group ->
                                    scope.launch(Dispatchers.IO) {
                                        todoDao.deleteGroup(
                                            TodoGroupEntity(
                                                group.id,
                                                group.title,
                                                group.tasks,
                                                group.createdAt,
                                                group.isPinned
                                            )
                                        )
                                    }
                                },
                                // ✅ THE FIX: Provide the missing logic here
                                onTogglePin = { group ->
                                    scope.launch(Dispatchers.IO) {
                                        // Flip the pin state and save to database
                                        todoDao.insertGroup(
                                            TodoGroupEntity(
                                                id = group.id,
                                                title = group.title,
                                                tasks = group.tasks,
                                                createdAt = group.createdAt,
                                                isPinned = !group.isPinned // Toggle the value
                                            )
                                        )
                                    }
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
                            val id = backStack.arguments?.getString("groupId") ?: ""
                            val query = backStack.arguments?.getString("query") ?: "" // Extract query
                            val existing = groups.find { it.id == id } ?: TodoGroup(id = id)

                            AddTodoScreen(
                                existingGroup = existing,
                                highlightQuery = query, // ✅ Pass the query here!
                                onSave = { updated ->
                                    navController.popBackStack()
                                    scope.launch(Dispatchers.IO) {
                                        todoDao.insertGroup(
                                            TodoGroupEntity(
                                                updated.id,
                                                updated.title,
                                                updated.tasks,
                                                updated.createdAt,
                                                updated.isPinned
                                            )
                                        )
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}