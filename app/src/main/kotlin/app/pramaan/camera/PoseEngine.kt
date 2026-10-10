package app.pramaan.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import app.pramaan.core.manifest.PoseFrameJsonl
import app.pramaan.core.model.Landmark
import app.pramaan.core.model.PoseFrame
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap

class PoseEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private var landmarker: PoseLandmarker? = null

    // Callbacks for live processing
    var onPoseFrame: ((PoseFrame) -> Unit)? = null

    private var framesWriter: PrintWriter? = null
    private val recordedFrames = mutableListOf<PoseFrame>()
    private var isRecording = false

    // Thread-safe map tracking exact nanosecond timestamps across async MediaPipe calls
    private val timestampMap = ConcurrentHashMap<Long, Long>()

    // Measured FPS tracking
    private var recentFrameTimestamps = ArrayDeque<Long>()
    var measuredFps: Int = 30
        private set

    init {
        setupLandmarker()
    }

    private fun setupLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("models/pose_landmarker_lite.task")
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: PoseLandmarkerResult, _: MPImage ->
                    handleResult(result)
                }
                .setErrorListener { error: RuntimeException ->
                    error.printStackTrace()
                }
                .build()

            landmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    fun processCameraFrame(imageProxy: ImageProxy) {
        val landmarkerInstance = landmarker ?: run {
            imageProxy.close()
            return
        }

        val timestampNs = imageProxy.imageInfo.timestamp
        val timestampMs = timestampNs / 1_000_000L
        timestampMap[timestampMs] = timestampNs

        // Keep timestampMap bounded
        if (timestampMap.size > 100) {
            val oldest = timestampMap.keys.minOrNull()
            if (oldest != null) timestampMap.remove(oldest)
        }

        try {
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            landmarkerInstance.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    private fun handleResult(result: PoseLandmarkerResult) {
        val timestampMs = result.timestampMs()
        val timestampNs = timestampMap.remove(timestampMs) ?: (timestampMs * 1_000_000L)

        // Update measured FPS
        synchronized(recentFrameTimestamps) {
            recentFrameTimestamps.addLast(timestampNs)
            while (recentFrameTimestamps.size > 30) {
                recentFrameTimestamps.removeFirst()
            }
            if (recentFrameTimestamps.size >= 5) {
                val dtSec = (recentFrameTimestamps.last() - recentFrameTimestamps.first()) / 1_000_000_000.0
                if (dtSec > 0.05) {
                    val currentRate = Math.round((recentFrameTimestamps.size - 1) / dtSec).toInt()
                    if (currentRate in 10..120) {
                        measuredFps = currentRate
                    }
                }
            }
        }

        val landmarksList = result.landmarks()
        val landmarks = if (landmarksList.isNotEmpty() && landmarksList[0].size >= 33) {
            landmarksList[0].map { lm ->
                val vis = if (lm.visibility().isPresent) lm.visibility().get() else 1.0f
                Landmark(x = lm.x(), y = lm.y(), visibility = vis)
            }
        } else {
            emptyList()
        }

        if (landmarks.size == 33) {
            val frame = PoseFrame(t = timestampNs, landmarks = landmarks)
            scope.launch {
                onPoseFrame?.invoke(frame)
                if (isRecording) {
                    recordedFrames.add(frame)
                    framesWriter?.println(PoseFrameJsonl.writeLine(frame))
                    framesWriter?.flush()
                }
            }
        }
    }

    fun startRecording(targetFile: File) {
        recordedFrames.clear()
        if (targetFile.parentFile?.exists() == false) {
            targetFile.parentFile?.mkdirs()
        }
        framesWriter = PrintWriter(FileWriter(targetFile, false))
        isRecording = true
    }

    fun stopRecording(): List<PoseFrame> {
        isRecording = false
        framesWriter?.flush()
        framesWriter?.close()
        framesWriter = null
        return recordedFrames.toList()
    }

    fun close() {
        landmarker?.close()
        landmarker = null
    }
}
