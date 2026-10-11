package app.pramaan.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.pramaan.AppContainer
import app.pramaan.auth.Role
import app.pramaan.core.model.TestType
import app.pramaan.ui.athlete.AthleteHomeScreen
import app.pramaan.ui.athlete.CertificateScreen
import app.pramaan.ui.athlete.ProfileScreen
import app.pramaan.ui.athlete.ShowQrScreen
import app.pramaan.ui.athlete.SubmissionDetailScreen
import app.pramaan.ui.athlete.SubmissionsScreen
import app.pramaan.ui.coach.CoachHistoryScreen
import app.pramaan.ui.coach.CoachHomeScreen
import app.pramaan.ui.coach.CoachScanScreen
import app.pramaan.ui.coach.DecisionDoneScreen
import app.pramaan.ui.coach.ReviewScreen
import app.pramaan.ui.onboarding.AthleteRegisterScreen
import app.pramaan.ui.onboarding.CoachLoginScreen
import app.pramaan.ui.onboarding.ConnectScreen
import app.pramaan.ui.onboarding.WelcomeScreen
import app.pramaan.ui.screens.ImportScreen
import app.pramaan.ui.screens.RecordScreen
import app.pramaan.ui.screens.ResultScreen
import app.pramaan.ui.screens.TestSetupScreen
import app.pramaan.ui.screens.TestSetupViewModel
import app.pramaan.ui.theme.PramaanNavy
import kotlinx.coroutines.flow.first

object Routes {
    const val SPLASH = "splash"
    const val CONNECT = "connect"
    const val WELCOME = "welcome"
    const val ATHLETE_REGISTER = "athlete_register"
    const val COACH_LOGIN = "coach_login"

    const val ATHLETE_HOME = "athlete_home"
    const val COACH_HOME = "coach_home"
    const val COACH_SCAN = "coach_scan"

    const val COACH_REVIEW = "coach_review/{sessionId}?isScanned={isScanned}&scannedQr={scannedQr}"
    fun coachReview(sessionId: String, isScanned: Boolean = false, scannedQr: String? = null): String {
        val qrPart = if (!scannedQr.isNullOrBlank()) "&scannedQr=${Uri.encode(scannedQr)}" else ""
        return "coach_review/$sessionId?isScanned=$isScanned$qrPart"
    }

    const val COACH_DECISION_DONE = "coach_decision_done/{sessionId}/{isApproved}?certJws={certJws}"
    fun coachDecisionDone(sessionId: String, isApproved: Boolean, certJws: String? = null): String {
        val certPart = if (!certJws.isNullOrBlank()) "?certJws=${Uri.encode(certJws)}" else ""
        return "coach_decision_done/$sessionId/$isApproved$certPart"
    }

    const val COACH_HISTORY = "coach_history"

    const val TEST_SETUP = "test_setup/{testType}"
    fun testSetup(testType: TestType) = "test_setup/${testType.name}"

    const val RECORD = "record/{athleteId}/{testType}"
    fun record(athleteId: String, testType: TestType) = "record/$athleteId/${testType.name}"

    const val RESULT = "result/{sessionId}"
    fun result(sessionId: String) = "result/$sessionId"

    const val SHOW_QR = "show_qr/{sessionId}"
    fun showQr(sessionId: String) = "show_qr/$sessionId"

    const val SUBMISSIONS = "submissions"

    const val SUBMISSION_DETAIL = "submission_detail/{sessionId}"
    fun submissionDetail(sessionId: String) = "submission_detail/$sessionId"

    const val CERTIFICATE = "certificate/{sessionId}"
    fun certificate(sessionId: String) = "certificate/$sessionId"

    const val PROFILE = "profile"
    const val IMPORT = "import"
}

@Composable
fun PramaanNavHost(
    navController: NavHostController,
    container: AppContainer
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PramaanNavy)
            }

            LaunchedEffect(Unit) {
                val serverUrl = container.authStore.serverUrlFlow.first()
                val token = container.authStore.tokenFlow.first()
                val role = container.authStore.roleFlow.first()

                val isServerConfigured = !serverUrl.isNullOrBlank() && serverUrl != "http://10.0.2.2:8080"

                val dest = when {
                    !isServerConfigured -> Routes.CONNECT
                    token.isNullOrBlank() -> Routes.WELCOME
                    role == Role.ATHLETE -> Routes.ATHLETE_HOME
                    role == Role.COACH -> Routes.COACH_HOME
                    else -> Routes.WELCOME
                }
                navController.navigate(dest) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
        }

        composable(Routes.CONNECT) {
            ConnectScreen(
                container = container,
                onConnected = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.CONNECT) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.WELCOME) {
            WelcomeScreen(
                container = container,
                onSelectAthlete = {
                    navController.navigate(Routes.ATHLETE_REGISTER)
                },
                onSelectCoach = {
                    navController.navigate(Routes.COACH_LOGIN)
                },
                onChangeServer = {
                    navController.navigate(Routes.CONNECT)
                }
            )
        }

        composable(Routes.ATHLETE_REGISTER) {
            AthleteRegisterScreen(
                container = container,
                onRegistered = {
                    navController.navigate(Routes.ATHLETE_HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
                onChangeServer = {
                    navController.navigate(Routes.CONNECT)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.COACH_LOGIN) {
            CoachLoginScreen(
                container = container,
                onLoggedIn = {
                    navController.navigate(Routes.COACH_HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
                onChangeServer = {
                    navController.navigate(Routes.CONNECT)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // ================= ATHLETE GRAPH =================
        composable(Routes.ATHLETE_HOME) {
            AthleteHomeScreen(
                container = container,
                onStartTest = { testType ->
                    navController.navigate(Routes.testSetup(testType))
                },
                onNavigateToSubmissions = {
                    navController.navigate(Routes.SUBMISSIONS)
                },
                onNavigateToSubmissionDetail = { sessionId ->
                    navController.navigate(Routes.submissionDetail(sessionId))
                },
                onNavigateToProfile = {
                    navController.navigate(Routes.PROFILE)
                }
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
                        popUpTo(Routes.ATHLETE_HOME)
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
                onSubmitForCoach = { sid ->
                    navController.navigate(Routes.showQr(sid))
                },
                onNavigateToCertificate = { sid ->
                    navController.navigate(Routes.certificate(sid))
                },
                onBack = {
                    navController.navigate(Routes.ATHLETE_HOME) {
                        popUpTo(Routes.ATHLETE_HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.SHOW_QR,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            ShowQrScreen(
                container = container,
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onViewCertificate = { sid ->
                    navController.navigate(Routes.certificate(sid))
                }
            )
        }

        composable(Routes.SUBMISSIONS) {
            SubmissionsScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onSelectSubmission = { sid ->
                    navController.navigate(Routes.submissionDetail(sid))
                }
            )
        }

        composable(
            route = Routes.SUBMISSION_DETAIL,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            SubmissionDetailScreen(
                container = container,
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onShowQr = { sid ->
                    navController.navigate(Routes.showQr(sid))
                },
                onViewCertificate = { sid ->
                    navController.navigate(Routes.certificate(sid))
                }
            )
        }

        composable(
            route = Routes.CERTIFICATE,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            CertificateScreen(
                sessionId = sessionId,
                container = container,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                container = container,
                onNavigateToImport = {
                    navController.navigate(Routes.IMPORT)
                },
                onSignOut = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
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

        // ================= COACH GRAPH =================
        composable(Routes.COACH_HOME) {
            CoachHomeScreen(
                container = container,
                onScanQr = {
                    navController.navigate(Routes.COACH_SCAN)
                },
                onSelectSession = { sessionId, isScanned ->
                    navController.navigate(Routes.coachReview(sessionId, isScanned))
                },
                onViewHistory = {
                    navController.navigate(Routes.COACH_HISTORY)
                },
                onLogout = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.COACH_SCAN) {
            CoachScanScreen(
                onSessionScanned = { sessionId, rawQr ->
                    navController.navigate(Routes.coachReview(sessionId, isScanned = true, scannedQr = rawQr)) {
                        popUpTo(Routes.COACH_HOME)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.COACH_REVIEW,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("isScanned") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("scannedQr") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val isScanned = backStackEntry.arguments?.getBoolean("isScanned") ?: false
            val scannedQr = backStackEntry.arguments?.getString("scannedQr")

            ReviewScreen(
                container = container,
                sessionId = sessionId,
                isScanned = isScanned,
                scannedQr = scannedQr,
                onDecisionDone = { sid, isApproved, certJws ->
                    navController.navigate(Routes.coachDecisionDone(sid, isApproved, certJws)) {
                        popUpTo(Routes.COACH_HOME)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.COACH_DECISION_DONE,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("isApproved") { type = NavType.BoolType },
                navArgument("certJws") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val isApproved = backStackEntry.arguments?.getBoolean("isApproved") ?: false
            val certJws = backStackEntry.arguments?.getString("certJws")

            DecisionDoneScreen(
                container = container,
                sessionId = sessionId,
                isApproved = isApproved,
                certificateJws = certJws,
                onBackToQueue = {
                    navController.navigate(Routes.COACH_HOME) {
                        popUpTo(Routes.COACH_HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.COACH_HISTORY) {
            CoachHistoryScreen(
                container = container,
                onViewCertificate = { sessionId ->
                    navController.navigate(Routes.certificate(sessionId))
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
