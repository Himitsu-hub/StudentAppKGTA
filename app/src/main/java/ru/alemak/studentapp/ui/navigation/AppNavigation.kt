package ru.alemak.studentapp.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.alemak.studentapp.ui.campus.CampusScreen
import ru.alemak.studentapp.ui.home.HomeScreen
import ru.alemak.studentapp.ui.onboarding.OnboardingScreen
import ru.alemak.studentapp.ui.reminders.RemindersScreen
import ru.alemak.studentapp.ui.schedule.ScheduleScreen
import ru.alemak.studentapp.ui.teachers.TeacherDetailScreen
import ru.alemak.studentapp.ui.teachers.TeachersScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SCHEDULE = "schedule"
    const val TEACHERS = "teachers"
    const val TEACHER_DETAIL = "teacher/{name}"
    const val REMINDERS = "reminders"
    const val CAMPUS = "campus"

    fun teacherDetail(name: String) = "teacher/${Uri.encode(name)}"
}

@Composable
fun AppNavigation(
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    val prefsReady by themeViewModel.prefsReady.collectAsStateWithLifecycle()
    val onboardingDone by themeViewModel.onboardingDone.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    if (!prefsReady) return

    val startDest = if (onboardingDone) Routes.HOME else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = startDest) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            val dark by themeViewModel.darkTheme.collectAsStateWithLifecycle()
            HomeScreen(
                onOpenSchedule = { navController.navigate(Routes.SCHEDULE) },
                onOpenTeachers = { navController.navigate(Routes.TEACHERS) },
                onOpenReminders = { navController.navigate(Routes.REMINDERS) },
                onOpenCampus = { navController.navigate(Routes.CAMPUS) },
                darkTheme = dark,
                onToggleTheme = { themeViewModel.toggleDarkTheme() },
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
