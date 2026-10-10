package app.pramaan.server

import app.pramaan.core.model.Badge
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SessionsTable : Table("sessions") {
    val id = varchar("id", 64)
    val athleteId = varchar("athlete_id", 64)
    val athleteInitials = varchar("athlete_initials", 16)
    val test = varchar("test", 64)
    val best = double("best")
    val unit = varchar("unit", 16)
    val badge = varchar("badge", 32)
    val capturedAt = varchar("captured_at", 64)
    val syncedAt = varchar("synced_at", 64)
    val rawManifest = text("raw_manifest")
    val certJws = text("cert_jws").nullable()

    override val primaryKey = PrimaryKey(id)
}

data class ServerSessionRecord(
    val id: String,
    val athleteId: String,
    val athleteInitials: String,
    val test: String,
    val best: Double,
    val unit: String,
    val badge: Badge,
    val capturedAt: String,
    val syncedAt: String,
    val rawManifest: String,
    val certJws: String?
)

object ServerDb {
    fun init(dbFile: File = File("pramaan_server.db")) {
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(SessionsTable)
        }
    }

    fun save(record: ServerSessionRecord) {
        transaction {
            SessionsTable.insert {
                it[id] = record.id
                it[athleteId] = record.athleteId
                it[athleteInitials] = record.athleteInitials
                it[test] = record.test
                it[best] = record.best
                it[unit] = record.unit
                it[badge] = record.badge.name
                it[capturedAt] = record.capturedAt
                it[syncedAt] = record.syncedAt
                it[rawManifest] = record.rawManifest
                it[certJws] = record.certJws
            }
        }
    }

    fun getAll(): List<ServerSessionRecord> = transaction {
        SessionsTable.selectAll().map { row ->
            ServerSessionRecord(
                id = row[SessionsTable.id],
                athleteId = row[SessionsTable.athleteId],
                athleteInitials = row[SessionsTable.athleteInitials],
                test = row[SessionsTable.test],
                best = row[SessionsTable.best],
                unit = row[SessionsTable.unit],
                badge = try { Badge.valueOf(row[SessionsTable.badge]) } catch (_: Exception) { Badge.VERIFIED },
                capturedAt = row[SessionsTable.capturedAt],
                syncedAt = row[SessionsTable.syncedAt],
                rawManifest = row[SessionsTable.rawManifest],
                certJws = row[SessionsTable.certJws]
            )
        }
    }

    fun getById(id: String): ServerSessionRecord? = transaction {
        SessionsTable.selectAll().where { SessionsTable.id eq id }.firstOrNull()?.let { row ->
            ServerSessionRecord(
                id = row[SessionsTable.id],
                athleteId = row[SessionsTable.athleteId],
                athleteInitials = row[SessionsTable.athleteInitials],
                test = row[SessionsTable.test],
                best = row[SessionsTable.best],
                unit = row[SessionsTable.unit],
                badge = try { Badge.valueOf(row[SessionsTable.badge]) } catch (_: Exception) { Badge.VERIFIED },
                capturedAt = row[SessionsTable.capturedAt],
                syncedAt = row[SessionsTable.syncedAt],
                rawManifest = row[SessionsTable.rawManifest],
                certJws = row[SessionsTable.certJws]
            )
        }
    }
}
