package ru.alemak.studentapp.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.alemak.studentapp.ui.campus.CampusScreen
import ru.alemak.studentapp.ui.home.HomeScreen
import ru.alemak.studentapp.ui.reminders.RemindersScreen
import ru.alemak.studentapp.ui.schedule.ScheduleScreen
import ru.alemak.studentapp.ui.teachers.TeacherDetailScreen
import ru.alemak.studentapp.ui.teachers.TeachersScreen

object Routes {
    const val HOME = "home"
    const val SCHEDULE = "schedule"
    const val TEACHERS = "teachers"
    const val TEACHER_DETAIL = "teacher/{name}"
    const val REMINDERS = "reminders"
    const val CAMPUS = "campus"

    fun teacherDetail(name: String) = "teacher/${Uri.encode(name)}"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSchedule = { navController.navigate(Routes.SCHEDULE) },
                onOpenTeachers = { navController.navigate(Routes.TEACHERS) },
                onOpenReminders = { navController.navigate(Routes.REMINDERS) },
                onOpenCampus = { navController.navigate(Routes.CAMPUS) },
            )
        }
        composable(Routes.SCHEDULE) {
            ScheduleScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TEACHERS) {
            TeachersScreen(
                onBack = { navController.popBackStack() },
                onOpenTeacher = { name ->
                    navController.navigate(Routes.teacherDetail(name))
                },
            )
        }
        composable(
            route = Routes.TEACHER_DETAIL,
            arguments = listOf(navArgument("name") { type = NavType.StringType }),
        ) { entry ->
            val name = Uri.decode(entry.arguments?.getString("name").orEmpty())
            TeacherDetailScreen(
                teacherName = name,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.REMINDERS) {
            RemindersScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CAMPUS) {
            CampusScreen(onBack = { navController.popBackStack() })
        }
    }
}
