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
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    val poseEngine: PoseEngine
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var finalizeDeferred: CompletableDeferred<Boolean>? = null
    var isBound: Boolean = false
        private set

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onBound: (Boolean) -> Unit = {}
    ) {
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

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
                    isBound = true
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
                        isBound = true
                        onBound(true)
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                        isBound = false
                        onBound(false)
                    }
                }
            } catch (err: Exception) {
                err.printStackTrace()
                isBound = false
                onBound(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording(
        videoFile: File,
        onEvent: (VideoRecordEvent) -> Unit = {}
    ): Boolean {
        val vc = videoCapture ?: return false
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        finalizeDeferred = null

        activeRecording = vc.output
            .prepareRecording(context, outputOptions)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    finalizeDeferred?.complete(!event.hasError())
                }
                onEvent(event)
            }
        return true
    }

    suspend fun stopAndAwaitFinalize(timeoutMs: Long = 4000L): Boolean {
        val recording = activeRecording ?: return true
        val deferred = CompletableDeferred<Boolean>()
        finalizeDeferred = deferred

        recording.stop()
        activeRecording = null

        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: false

        finalizeDeferred = null
        return result
    }

    fun unbind() {
        activeRecording?.stop()
        activeRecording = null
        finalizeDeferred = null
        cameraProvider?.unbindAll()
        isBound = false
    }
}
