package app.pramaan.server

import app.pramaan.core.crypto.EcdsaVerifier
import app.pramaan.core.crypto.Sha256
import app.pramaan.core.manifest.ReviewPayload
import app.pramaan.core.manifest.SessionQr
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.Period
import kotlin.math.abs

sealed class ReviewResult {
    data class Success(
        val sessionId: String,
        val status: String,
        val integrity: String,
        val certificateJws: String?,
        val verifyUrl: String?
    ) : ReviewResult()

    data class Error(val statusCode: Int, val error: String, val message: String) : ReviewResult()
}

class ReviewService(
    private val accountRepo: AccountRepo,
    private val eventLog: EventLog,
    private val certificateIssuer: ServerCertificateIssuer,
    private val baseUrl: () -> String = { "http://localhost:8080" }
) {

    fun reviewSession(
        sessionId: String,
        coachId: String,
        decision: String,
        requestedMethod: String,
        reason: String?,
        scannedQr: String?,
        reviewedAt: Long,
        coachSignature: String
    ): ReviewResult {
        val coach = accountRepo.getAccount(coachId)
            ?: return ReviewResult.Error(401, "UNAUTHORIZED", "Coach not found")
        if (coach.role != "COACH") {
            return ReviewResult.Error(403, "FORBIDDEN", "Account is not a coach")
        }
        if (coach.devicePubkey.isNullOrBlank()) {
            return ReviewResult.Error(401, "UNBOUND_DEVICE", "Coach device key not registered")
        }

        val sessionRow = transaction {
            SessionsTable.selectAll().where { SessionsTable.id eq sessionId }.singleOrNull()
        } ?: return ReviewResult.Error(404, "NOT_FOUND", "Session not found")

        val currentStatus = sessionRow[SessionsTable.status]
        if (currentStatus != "AWAITING_REVIEW") {
            return ReviewResult.Error(409, "ALREADY_REVIEWED", "Session status is $currentStatus, not AWAITING_REVIEW")
        }

        val integrity = sessionRow[SessionsTable.integrity] ?: "UNKNOWN"
        if (decision == "APPROVED" && integrity != "VERIFIED") {
            return ReviewResult.Error(422, "NOT_APPROVABLE", "Session integrity is $integrity. Only VERIFIED sessions can be approved.")
        }

        if (decision == "REJECTED" && reason.isNullOrBlank()) {
            return ReviewResult.Error(400, "REASON_REQUIRED", "Rejection reason is required")
        }

        val manifestSha256 = sessionRow[SessionsTable.manifestSha256] ?: ""
        val athleteId = sessionRow[SessionsTable.athleteId]
        val athlete = if (athleteId != null) accountRepo.getAccount(athleteId) else null

        // 4. In-person QR validation
        var finalMethod = requestedMethod
        var qrSha256 = ""
        if (requestedMethod == "IN_PERSON_QR") {
            if (scannedQr.isNullOrBlank()) {
                finalMethod = "REMOTE"
            } else {
                val decodedQr = SessionQr.decode(scannedQr)
                if (decodedQr == null || decodedQr.sessionId != sessionId || decodedQr.manifestSha256Hex != manifestSha256) {
                    finalMethod = "REMOTE"
                } else if (athlete == null || athlete.devicePubkey.isNullOrBlank() || !decodedQr.verifySignature(athlete.devicePubkey)) {
                    finalMethod = "REMOTE"
                } else if (abs(reviewedAt - decodedQr.issuedAtMs) > 600_000) {
                    // QR issued more than 10 minutes ago
                    finalMethod = "REMOTE"
                } else {
                    qrSha256 = Sha256.digest(scannedQr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                }
            }
        }

        // 5. Verify coach signature over canonical ReviewPayload
        val reviewPayload = ReviewPayload(
            coachId = coach.id,
            decision = decision,
            manifestSha256 = manifestSha256,
            method = finalMethod,
            qrSha256 = qrSha256,
            reviewedAt = reviewedAt,
            sessionId = sessionId
        )

        val isCoachSigValid = EcdsaVerifier.verify(
            publicKeyBase64Url = coach.devicePubkey,
            message = reviewPayload.canonicalBytes(),
            signatureDerBase64Url = coachSignature
        )

        if (!isCoachSigValid) {
            return ReviewResult.Error(401, "BAD_SIGNATURE", "Coach signature verification failed")
        }

        // 6 & 7. Update session and log events
        var certJws: String? = null
        if (decision == "APPROVED") {
            val athName = athlete?.name ?: "Athlete"
            val athAge = athlete?.dob?.let {
                try {
                    Period.between(LocalDate.parse(it), LocalDate.now()).years
                } catch (_: Exception) {
                    null
                }
            }
            val athSex = athlete?.sex
            val test = sessionRow[SessionsTable.test]
            val best = sessionRow[SessionsTable.best]
            val unit = sessionRow[SessionsTable.unit]

            certJws = certificateIssuer.issueCoachVerifiedCertificate(
                sessionId = sessionId,
                manifestSha256 = manifestSha256,
                athleteId = athleteId ?: "ATH-UNKNOWN",
                athleteName = athName,
                athleteAge = athAge,
                athleteSex = athSex,
                test = test,
                score = best,
                unit = unit,
                integrity = integrity,
                coachId = coach.id,
                coachName = coach.name,
                reviewMethod = finalMethod,
                reviewedAt = reviewedAt
            )
        }

        transaction {
            SessionsTable.update({ SessionsTable.id eq sessionId }) {
                it[status] = decision
                it[reviewedBy] = coach.id
                it[SessionsTable.reviewedAt] = reviewedAt
                it[reviewMethod] = finalMethod
                it[reviewReason] = reason
                it[coachSig] = coachSignature
                if (certJws != null) {
                    it[certificateJws] = certJws
                }
            }
        }

        if (finalMethod == "IN_PERSON_QR") {
            eventLog.log(sessionId, "QR_SCANNED", coach.id, """{"scannedQrSha256":"$qrSha256"}""")
        }
        eventLog.log(sessionId, "COACH_REVIEWED", coach.id, """{"decision":"$decision","method":"$finalMethod","reason":${reason?.let { "\"$it\"" } ?: "null"}}""")

        if (certJws != null) {
            eventLog.log(sessionId, "CERT_ISSUED", "SERVER", """{"jwsLength":${certJws.length}}""")
        }

        val verifyUrl = if (certJws != null) "${baseUrl().removeSuffix("/")}/v/$certJws" else null
        return ReviewResult.Success(
            sessionId = sessionId,
            status = decision,
            integrity = integrity,
            certificateJws = certJws,
            verifyUrl = verifyUrl
        )
    }
}
