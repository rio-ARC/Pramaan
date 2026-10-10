package app.pramaan.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executors

enum class CameraState {
    IDLE,
    BOUND,
    ERROR
}

class CameraController(
    private val context: Context,
    val poseEngine: PoseEngine
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val _cameraState = MutableStateFlow(CameraState.IDLE)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private var finalizeDeferred: CompletableDeferred<File?>? = null
    private var currentRecordingFile: File? = null

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onBound: (Boolean) -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                val vc = VideoCapture.withOutput(recorder)
                videoCapture = vc

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    poseEngine.processCameraFrame(imageProxy)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        vc,
                        imageAnalysis
                    )
                    _cameraState.value = CameraState.BOUND
                    onBound(true)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Graceful fallback if 3 streams simultaneously not supported on hardware
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        _cameraState.value = CameraState.BOUND
                        onBound(true)
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                        _cameraState.value = CameraState.ERROR
                        onBound(false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _cameraState.value = CameraState.ERROR
                onBound(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording(
        videoFile: File,
        onEvent: (VideoRecordEvent) -> Unit = {}
    ): Boolean {
        val vc = videoCapture ?: return false
        currentRecordingFile = videoFile
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        activeRecording = vc.output
            .prepareRecording(context, outputOptions)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (event.hasError()) {
                        finalizeDeferred?.complete(null)
                    } else {
                        finalizeDeferred?.complete(videoFile)
                    }
                }
                onEvent(event)
            }
        return true
    }

    suspend fun stopAndAwaitFinalize(): File? {
        val rec = activeRecording
        val file = currentRecordingFile
        if (rec == null) {
            return file?.takeIf { it.exists() }
        }

        val deferred = CompletableDeferred<File?>()
        finalizeDeferred = deferred
        try {
            rec.stop()
            activeRecording = null
            return withTimeoutOrNull(5000L) {
                deferred.await()
            } ?: file?.takeIf { it.exists() }
        } catch (e: Exception) {
            return file?.takeIf { it.exists() }
        } finally {
            finalizeDeferred = null
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        _cameraState.value = CameraState.IDLE
    }
}

