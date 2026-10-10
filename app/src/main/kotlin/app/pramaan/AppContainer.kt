package app.pramaan

import android.content.Context
import app.pramaan.api.ApiClient
import app.pramaan.auth.AuthRepository
import app.pramaan.auth.AuthStore
import app.pramaan.camera.CameraController
import app.pramaan.camera.GyroRecorder
import app.pramaan.camera.PoseEngine
import app.pramaan.camera.VideoFilePoseRunner
import app.pramaan.data.AthleteDao
import app.pramaan.data.PramaanDb
import app.pramaan.data.SessionDao
import app.pramaan.data.SessionStore
import app.pramaan.seal.DeviceKey
import app.pramaan.seal.LocalVerifier
import app.pramaan.seal.Sealer
import app.pramaan.seal.TamperSimulator
import app.pramaan.submissions.SubmissionRepository
import app.pramaan.witness.WitnessSigner

class AppContainer(val context: Context) {
    val db: PramaanDb by lazy { PramaanDb.getDatabase(context) }
    val athleteDao: AthleteDao by lazy { db.athleteDao() }
    val sessionDao: SessionDao by lazy { db.sessionDao() }

    val sessionStore: SessionStore by lazy { SessionStore(context) }
    val deviceKey: DeviceKey by lazy { DeviceKey() }

    val authStore: AuthStore by lazy { AuthStore(context) }
    val apiClient: ApiClient by lazy { ApiClient(authStore) }
    val authRepository: AuthRepository by lazy { AuthRepository(authStore, apiClient, deviceKey) }
    val submissionRepository: SubmissionRepository by lazy {
        SubmissionRepository(apiClient, sessionDao, sessionStore, authStore)
    }
    val reviewSigner: app.pramaan.review.ReviewSigner by lazy {
        app.pramaan.review.ReviewSigner(deviceKey, authStore)
    }
    val reviewRepository: app.pramaan.review.ReviewRepository by lazy {
        app.pramaan.review.ReviewRepository(apiClient, reviewSigner)
    }

    val sealer: Sealer by lazy { Sealer(sessionStore, deviceKey, sessionDao) }
    val localVerifier: LocalVerifier by lazy { LocalVerifier(sessionStore, sessionDao) }
    val tamperSimulator: TamperSimulator by lazy { TamperSimulator(sessionStore, localVerifier, sessionDao) }
    val witnessSigner: WitnessSigner by lazy { WitnessSigner(deviceKey) }

    val gyroRecorder: GyroRecorder by lazy { GyroRecorder(context) }
    val poseEngine: PoseEngine by lazy { PoseEngine(context) }
    val cameraController: CameraController by lazy { CameraController(context, poseEngine) }
    val videoFilePoseRunner: VideoFilePoseRunner by lazy { VideoFilePoseRunner(context) }
}
