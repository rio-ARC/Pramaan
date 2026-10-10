package app.pramaan.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.pramaan.AppContainer
import app.pramaan.core.model.TestType
import app.pramaan.ui.screens.AthleteEditScreen
import app.pramaan.ui.screens.AthleteEditViewModel
import app.pramaan.ui.screens.AthletesScreen
import app.pramaan.ui.screens.AthletesViewModel
import app.pramaan.ui.screens.CoSignScreen
import app.pramaan.ui.screens.HomeScreen
import app.pramaan.ui.screens.HomeViewModel
import app.pramaan.ui.screens.ImportScreen
import app.pramaan.ui.screens.RecordScreen
import app.pramaan.ui.screens.ResultScreen
import app.pramaan.ui.screens.SyncScreen
import app.pramaan.ui.screens.TestSetupScreen
import app.pramaan.ui.screens.TestSetupViewModel
import app.pramaan.ui.screens.WitnessScreen

object Routes {
    const val HOME = "home"
    const val ATHLETES = "athletes"
    const val ATHLETE_EDIT = "athlete_edit/{athleteId}"
    fun athleteEdit(id: String) = "athlete_edit/$id"

    const val TEST_SETUP = "test_setup/{testType}"
    fun testSetup(testType: TestType) = "test_setup/${testType.name}"

    const val RECORD = "record/{athleteId}/{testType}"
    fun record(athleteId: String, testType: TestType) = "record/$athleteId/${testType.name}"

    const val RESULT = "result/{sessionId}"
    fun result(sessionId: String) = "result/$sessionId"

    const val COSIGN = "cosign/{sessionId}"
    fun cosign(sessionId: String) = "cosign/$sessionId"

    const val WITNESS = "witness"
    const val IMPORT = "import"
    const val SYNC = "sync"
}

@Composable
fun PramaanNavHost(
    navController: NavHostController,
    container: AppContainer
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            val vm = HomeViewModel(container)
            HomeScreen(
                viewModel = vm,
                onNavigateToSetup = { testType ->
                    navController.navigate(Routes.testSetup(testType))
                },
                onNavigateToAthletes = {
                    navController.navigate(Routes.ATHLETES)
                },
                onNavigateToWitness = {
                    navController.navigate(Routes.WITNESS)
                },
                onNavigateToImport = {
                    navController.navigate(Routes.IMPORT)
                },
                onNavigateToSync = {
                    navController.navigate(Routes.SYNC)
                },
                onNavigateToResult = { sessionId ->
                    navController.navigate(Routes.result(sessionId))
                }
            )
        }

        composable(Routes.ATHLETES) {
            val vm = AthletesViewModel(container)
            AthletesScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onAddAthlete = {
                    navController.navigate(Routes.athleteEdit("new"))
                },
                onEditAthlete = { id ->
                    navController.navigate(Routes.athleteEdit(id))
                },
                onSelectAthleteForTest = { _ ->
                    navController.navigate(Routes.testSetup(TestType.VERTICAL_JUMP))
                }
            )
        }

        composable(
            route = Routes.ATHLETE_EDIT,
            arguments = listOf(navArgument("athleteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val athleteId = backStackEntry.arguments?.getString("athleteId")
            val vm = AthleteEditViewModel(container)
            AthleteEditScreen(
                viewModel = vm,
                athleteId = if (athleteId == "new") null else athleteId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.TEST_SETUP,
            arguments = listOf(navArgument("testType") { type = NavType.StringType })
        ) { backStackEntry ->
            val testTypeName = backStackEntry.arguments?.getString("testType") ?: TestType.VERTICAL_JUMP.name
            val testType = try {
                TestType.valueOf(testTypeName)
            } catch (_: Exception) {
                TestType.VERTICAL_JUMP
            }
            val vm = TestSetupViewModel(container)
            TestSetupScreen(
                testType = testType,
                viewModel = vm,
                onBeginTest = { athleteId, type ->
                    navController.navigate(Routes.record(athleteId, type))
                },
                onNavigateToAthletes = {
                    navController.navigate(Routes.ATHLETES)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.RECORD,
            arguments = listOf(
                navArgument("athleteId") { type = NavType.StringType },
                navArgument("testType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val athleteId = backStackEntry.arguments?.getString("athleteId") ?: ""
            val testTypeName = backStackEntry.arguments?.getString("testType") ?: TestType.VERTICAL_JUMP.name
            val testType = try {
                TestType.valueOf(testTypeName)
            } catch (_: Exception) {
                TestType.VERTICAL_JUMP
            }
            RecordScreen(
                athleteId = athleteId,
                testType = testType,
                container = container,
                onFinished = { sessionId ->
                    navController.navigate(Routes.result(sessionId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.RESULT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            ResultScreen(
                sessionId = sessionId,
                container = container,
                onNavigateToCoSign = { sid ->
                    navController.navigate(Routes.cosign(sid))
                },
                onNavigateToCertificate = { sid ->
                    navController.navigate(Routes.cosign(sid))
                },
                onBack = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.COSIGN,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            CoSignScreen(
                sessionId = sessionId,
                container = container,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.WITNESS) {
            WitnessScreen(
                container = container,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.IMPORT) {
            ImportScreen(
                container = container,
                onNavigateToResult = { sessionId ->
                    navController.navigate(Routes.result(sessionId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SYNC) {
            SyncScreen(
                container = container,
                onNavigateToResult = { sessionId ->
                    navController.navigate(Routes.result(sessionId))
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
