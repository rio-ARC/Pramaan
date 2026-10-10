package app.pramaan.seal

import app.pramaan.core.integrity.VerificationOutcome
import app.pramaan.data.SessionDao
import app.pramaan.data.SessionEntity
import app.pramaan.data.SessionStore
import java.io.File
import java.io.RandomAccessFile

/**
 * Implements PRD M8 & Demo step 9:
 * Copies a genuine session folder, alters 4 KB in the middle of video.mp4,
 * re-verifies, producing a TAMPERED badge with cryptographic mismatch proof.
 */
class TamperSimulator(
    private val sessionStore: SessionStore,
    private val localVerifier: LocalVerifier,
    private val sessionDao: SessionDao
) {

    suspend fun simulateTampering(originalSessionId: String): Pair<String, VerificationOutcome> {
        val originalDir = sessionStore.getSessionDir(originalSessionId)
        val tamperedSessionId = "${originalSessionId}_tampered"
        val tamperedDir = sessionStore.getSessionDir(tamperedSessionId)

        // Copy all files from original to tampered folder
        originalDir.copyRecursively(tamperedDir, overwrite = true)

        // Corrupt 4 KB in the middle of video.mp4
        val tamperedVideo = File(tamperedDir, "video.mp4")
        if (tamperedVideo.exists() && tamperedVideo.length() > 8192) {
            val length = tamperedVideo.length()
            val offset = length / 2
            RandomAccessFile(tamperedVideo, "rw").use { raf ->
                raf.seek(offset)
                val garbage = ByteArray(4096) { 0xFF.toByte() }
                raf.write(garbage)
            }
        }

        // Create database record for tampered session copy
        val originalEntity = sessionDao.getById(originalSessionId)
        if (originalEntity != null) {
            val tamperedEntity = originalEntity.copy(
                id = tamperedSessionId,
                folder = tamperedDir.absolutePath,
                localIntegrity = "TAMPERED",
                status = "DRAFT",
                serverIntegrity = null,
                certificateJws = null
            )
            sessionDao.insert(tamperedEntity)
        }

        // Run local verifier to evaluate C1 and mark as TAMPERED
        val outcome = localVerifier.verify(tamperedSessionId)

        return tamperedSessionId to outcome
    }
}
