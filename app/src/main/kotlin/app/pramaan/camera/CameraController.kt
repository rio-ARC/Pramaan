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

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onBound: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
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
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
            onBound()
        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording(
        videoFile: File,
        onEvent: (VideoRecordEvent) -> Unit
    ) {
        val vc = videoCapture ?: return
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        activeRecording = vc.output
            .prepareRecording(context, outputOptions)
            .start(ContextCompat.getMainExecutor(context), onEvent)
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun unbind() {
        cameraProvider?.unbindAll()
    }
}
