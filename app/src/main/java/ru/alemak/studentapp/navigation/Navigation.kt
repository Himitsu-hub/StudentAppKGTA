package ru.alemak.studentapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.alemak.studentapp.screens.MainScreen
import ru.alemak.studentapp.screens.Screen1
import ru.alemak.studentapp.screens.Screen2
import ru.alemak.studentapp.screens.Screen3

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(navController = navController)
        }
        composable("screen1") {
            Screen1(navController = navController)
        }
        composable("screen2") {
            Screen2(navController = navController)
        }
        composable("screen3") {
            Screen3(navController = navController)
        }
    }
}