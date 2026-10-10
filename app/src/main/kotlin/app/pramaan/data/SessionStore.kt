package app.pramaan.data

import android.content.Context
import java.io.File

/**
 * Manages app-private session files stored under filesDir/sessions/{sessionId}/.
 * Enforces AGENTS.md rule 6 (never write video to the gallery).
 */
class SessionStore(private val context: Context) {

    private val sessionsRoot: File
        get() = File(context.filesDir, "sessions").apply { if (!exists()) mkdirs() }

    fun getSessionDir(sessionId: String): File {
        return File(sessionsRoot, sessionId).apply { if (!exists()) mkdirs() }
    }

    fun getVideoFile(sessionId: String): File = File(getSessionDir(sessionId), "video.mp4")
    fun getFramesFile(sessionId: String): File = File(getSessionDir(sessionId), "frames.jsonl")
    fun getGyroFile(sessionId: String): File = File(getSessionDir(sessionId), "gyro.jsonl")
    fun getManifestFile(sessionId: String): File = File(getSessionDir(sessionId), "manifest.json")
    fun getSigFile(sessionId: String): File = File(getSessionDir(sessionId), "manifest.sig")
    fun getWitnessFile(sessionId: String): File = File(getSessionDir(sessionId), "witness.json")
    fun getCertFile(sessionId: String): File = File(getSessionDir(sessionId), "cert.jws")

    fun getAllSessionDirs(): List<File> {
        return sessionsRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
    }
}
