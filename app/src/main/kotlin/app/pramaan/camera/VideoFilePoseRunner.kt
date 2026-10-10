package app.pramaan.camera

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import app.pramaan.core.manifest.PoseFrameJsonl
import app.pramaan.core.model.Landmark
import app.pramaan.core.model.PoseFrame
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

class VideoFilePoseRunner(private val context: Context) {

    /**
     * Extracts frames and generates frames.jsonl from an imported video file (PRD 10.1 step 5).
     * Calculates frame presentation timestamp from the video file itself:
     * t = index * duration / frameCount.
     */
    suspend fun processVideoFile(
        videoFile: File,
        outputFramesFile: File,
        onProgress: (Float) -> Unit = {}
    ): List<PoseFrame> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(videoFile.absolutePath)

        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1000L
        val frameCountMeta = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
        } else null

        val fps = 30
        val totalFrames = frameCountMeta ?: ((durationMs / 1000.0) * fps).toInt().coerceAtLeast(1)

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("models/pose_landmarker_lite.task")
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .build()
        val landmarker = PoseLandmarker.createFromOptions(context, options)

        val recordedFrames = mutableListOf<PoseFrame>()
        val writer = PrintWriter(FileWriter(outputFramesFile, false))

        try {
            for (i in 0 until totalFrames) {
                val tNs = (i.toDouble() * (durationMs * 1_000_000.0) / totalFrames).toLong()
                val timestampMs = tNs / 1_000_000L

                val timeUs = (i.toDouble() * (durationMs * 1000.0) / totalFrames).toLong()
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && frameCountMeta != null) {
                    try {
                        retriever.getFrameAtIndex(i)
                    } catch (_: Exception) {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }

                if (bitmap != null) {
                    val mpImage = BitmapImageBuilder(bitmap).build()
                    val result = landmarker.detectForVideo(mpImage, timestampMs)
                    val landmarksList = result.landmarks()
                    val landmarks = if (landmarksList.isNotEmpty() && landmarksList[0].size >= 33) {
                        landmarksList[0].map { lm ->
                            val vis = if (lm.visibility().isPresent) lm.visibility().get() else 1.0f
                            Landmark(x = lm.x(), y = lm.y(), visibility = vis)
                        }
                    } else emptyList()

                    if (landmarks.size == 33) {
                        val frame = PoseFrame(t = tNs, landmarks = landmarks)
                        recordedFrames.add(frame)
                        writer.println(PoseFrameJsonl.writeLine(frame))
                    }
                }
                onProgress((i + 1).toFloat() / totalFrames)
            }
        } finally {
            writer.flush()
            writer.close()
            landmarker.close()
            retriever.release()
        }

        recordedFrames
    }
}
