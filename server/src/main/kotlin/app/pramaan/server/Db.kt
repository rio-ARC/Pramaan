package app.pramaan.server

import app.pramaan.core.model.Badge
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object AccountsTable : Table("accounts") {
    val id = varchar("id", 64)
    val role = varchar("role", 16)
    val name = varchar("name", 128)
    val dob = varchar("dob", 32).nullable()
    val sex = varchar("sex", 8).nullable()
    val heightCm = double("height_cm").nullable()
    val weightKg = double("weight_kg").nullable()
    val district = varchar("district", 64).nullable()
    val phone = varchar("phone", 32).nullable()
    val guardianConsent = bool("guardian_consent").default(false)
    val photoPath = varchar("photo_path", 256).nullable()
    val pinHash = varchar("pin_hash", 128).nullable()
    val pinSalt = varchar("pin_salt", 64).nullable()
    val devicePubkey = text("device_pubkey").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object TokensTable : Table("tokens") {
    val tokenHash = varchar("token_hash", 128)
    val accountId = varchar("account_id", 64).references(AccountsTable.id)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(tokenHash)
}

object SessionsTable : Table("sessions") {
    val id = varchar("id", 64)
    val athleteId = varchar("athlete_id", 64).references(AccountsTable.id).nullable()
    val athleteInitials = varchar("athlete_initials", 16).nullable()
    val test = varchar("test", 64)
    val best = double("best")
    val unit = varchar("unit", 16)
    val status = varchar("status", 32).default("AWAITING_REVIEW")
    val integrity = varchar("integrity", 32).nullable()
    val integrityJson = text("integrity_json").nullable()
    val manifestSha256 = varchar("manifest_sha256", 128).nullable()
    val submittedAt = long("submitted_at").nullable()
    val reviewedBy = varchar("reviewed_by", 64).references(AccountsTable.id).nullable()
    val reviewedAt = long("reviewed_at").nullable()
    val reviewMethod = varchar("review_method", 32).nullable()
    val reviewReason = text("review_reason").nullable()
    val coachSig = text("coach_sig").nullable()
    val certificateJws = text("certificate_jws").nullable()
    val capturedAt = varchar("captured_at", 64).nullable()
    val rawManifest = text("raw_manifest").nullable()

    override val primaryKey = PrimaryKey(id)
}

object SessionEventsTable : Table("session_events") {
    val id = integer("id").autoIncrement()
    val sessionId = varchar("session_id", 64)
    val type = varchar("type", 32)
    val actorId = varchar("actor_id", 64).nullable()
    val at = long("at")
    val detail = text("detail").nullable()

    override val primaryKey = PrimaryKey(id)
}

data class AccountRecord(
    val id: String,
    val role: String,
    val name: String,
    val dob: String? = null,
    val sex: String? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val district: String? = null,
    val phone: String? = null,
    val guardianConsent: Boolean = false,
    val photoPath: String? = null,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val devicePubkey: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ServerSessionRecord(
    val id: String,
    val athleteId: String,
    val athleteInitials: String,
    val test: String,
    val best: Double,
    val unit: String,
    val status: String = "AWAITING_REVIEW",
    val integrity: String? = "VERIFIED",
    val integrityJson: String? = null,
    val manifestSha256: String? = null,
    val submittedAt: Long? = null,
    val reviewedBy: String? = null,
    val reviewedAt: Long? = null,
    val reviewMethod: String? = null,
    val reviewReason: String? = null,
    val coachSig: String? = null,
    val certificateJws: String? = null,
    val capturedAt: String? = null,
    val rawManifest: String? = null
) {
    val badge: Badge
        get() = when (integrity?.uppercase()) {
            "FLAGGED" -> Badge.FLAGGED
            "TAMPERED" -> Badge.TAMPERED
            "UNSEALED" -> Badge.UNSEALED
            else -> Badge.VERIFIED
        }
}

data class SessionEventRecord(
    val id: Int = 0,
    val sessionId: String,
    val type: String,
    val actorId: String?,
    val at: Long,
    val detail: String? = null
)

object ServerDb {
    fun init(dbFile: File = File("pramaan_server.db")) {
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", "org.sqlite.JDBC")
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                AccountsTable,
                TokensTable,
                SessionsTable,
                SessionEventsTable
            )
        }
    }

    private fun mapRow(row: org.jetbrains.exposed.sql.ResultRow): ServerSessionRecord {
        return ServerSessionRecord(
            id = row[SessionsTable.id],
            athleteId = row[SessionsTable.athleteId] ?: "",
            athleteInitials = row[SessionsTable.athleteInitials] ?: "",
            test = row[SessionsTable.test],
            best = row[SessionsTable.best],
            unit = row[SessionsTable.unit],
            status = row[SessionsTable.status],
            integrity = row[SessionsTable.integrity],
            integrityJson = row[SessionsTable.integrityJson],
            manifestSha256 = row[SessionsTable.manifestSha256],
            submittedAt = row[SessionsTable.submittedAt],
            reviewedBy = row[SessionsTable.reviewedBy],
            reviewedAt = row[SessionsTable.reviewedAt],
            reviewMethod = row[SessionsTable.reviewMethod],
            reviewReason = row[SessionsTable.reviewReason],
            coachSig = row[SessionsTable.coachSig],
            certificateJws = row[SessionsTable.certificateJws],
            capturedAt = row[SessionsTable.capturedAt],
            rawManifest = row[SessionsTable.rawManifest]
        )
    }

    fun getAll(): List<ServerSessionRecord> = transaction {
        SessionsTable.selectAll().map { mapRow(it) }
    }

    fun getById(id: String): ServerSessionRecord? = transaction {
        SessionsTable.selectAll().where { SessionsTable.id eq id }.firstOrNull()?.let { mapRow(it) }
    }

    fun getByAthleteId(athleteId: String): List<ServerSessionRecord> = transaction {
        SessionsTable.selectAll().where { SessionsTable.athleteId eq athleteId }
            .orderBy(SessionsTable.submittedAt to org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { mapRow(it) }
    }

    fun saveOrUpdate(record: ServerSessionRecord) {
        transaction {
            val exists = SessionsTable.selectAll().where { SessionsTable.id eq record.id }.count() > 0
            if (exists) {
                SessionsTable.update({ SessionsTable.id eq record.id }) {
                    it[athleteId] = record.athleteId
                    it[athleteInitials] = record.athleteInitials
                    it[test] = record.test
                    it[best] = record.best
                    it[unit] = record.unit
                    it[status] = record.status
                    it[integrity] = record.integrity
                    it[integrityJson] = record.integrityJson
                    it[manifestSha256] = record.manifestSha256
                    it[submittedAt] = record.submittedAt
                    it[reviewedBy] = record.reviewedBy
                    it[reviewedAt] = record.reviewedAt
                    it[reviewMethod] = record.reviewMethod
                    it[reviewReason] = record.reviewReason
                    it[coachSig] = record.coachSig
                    it[certificateJws] = record.certificateJws
                    it[capturedAt] = record.capturedAt
                    it[rawManifest] = record.rawManifest
                }
            } else {
                SessionsTable.insert {
                    it[id] = record.id
                    it[athleteId] = record.athleteId
                    it[athleteInitials] = record.athleteInitials
                    it[test] = record.test
                    it[best] = record.best
                    it[unit] = record.unit
                    it[status] = record.status
                    it[integrity] = record.integrity
                    it[integrityJson] = record.integrityJson
                    it[manifestSha256] = record.manifestSha256
                    it[submittedAt] = record.submittedAt
                    it[reviewedBy] = record.reviewedBy
                    it[reviewedAt] = record.reviewedAt
                    it[reviewMethod] = record.reviewMethod
                    it[reviewReason] = record.reviewReason
                    it[coachSig] = record.coachSig
                    it[certificateJws] = record.certificateJws
                    it[capturedAt] = record.capturedAt
                    it[rawManifest] = record.rawManifest
                }
            }
        }
    }
}
