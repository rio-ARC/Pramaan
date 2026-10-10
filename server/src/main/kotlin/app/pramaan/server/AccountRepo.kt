package app.pramaan.server

import app.pramaan.core.crypto.Sha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Serializable
data class SeedCoach(
    val id: String,
    val name: String,
    val district: String? = null,
    val pin: String
)

@Serializable
data class AthleteProfileInput(
    val name: String,
    val dob: String,
    val sex: String,
    val heightCm: Double,
    val weightKg: Double? = null,
    val district: String? = null,
    val phone: String? = null,
    val guardianConsent: Boolean = false,
    val devicePublicKey: String
)

object AccountRepo {
    private val random = SecureRandom()
    private val CROCKFORD_CHARS = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray()
    private val json = Json { ignoreUnknownKeys = true }

    fun generateAthleteId(): String {
        return transaction {
            var candidateId: String
            do {
                val suffix = (1..4).map { CROCKFORD_CHARS[random.nextInt(CROCKFORD_CHARS.size)] }.joinToString("")
                candidateId = "ATH-$suffix"
                val exists = AccountsTable.selectAll().where { AccountsTable.id eq candidateId }.count() > 0
            } while (exists)
            candidateId
        }
    }

    fun nextCoachId(): String {
        return transaction {
            val coachCount = AccountsTable.selectAll().where { AccountsTable.role eq "COACH" }.count()
            "COA-%02d".format(coachCount + 1)
        }
    }

    fun hashPin(pin: String, salt: String): String {
        val bytes = Sha256.digest((salt + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun createAthlete(
        profile: AthleteProfileInput,
        photoBytes: ByteArray?,
        photosDir: File
    ): AccountRecord {
        val athleteId = generateAthleteId()
        var photoPath: String? = null

        if (photoBytes != null && photoBytes.isNotEmpty()) {
            if (!photosDir.exists()) photosDir.mkdirs()
            val photoFile = File(photosDir, "$athleteId.jpg")
            photoFile.writeBytes(photoBytes)
            photoPath = photoFile.absolutePath
        }

        val now = System.currentTimeMillis()
        val record = AccountRecord(
            id = athleteId,
            role = "ATHLETE",
            name = profile.name.trim(),
            dob = profile.dob.trim(),
            sex = profile.sex.trim(),
            heightCm = profile.heightCm,
            weightKg = profile.weightKg,
            district = profile.district?.trim(),
            phone = profile.phone?.trim(),
            guardianConsent = profile.guardianConsent,
            photoPath = photoPath,
            devicePubkey = profile.devicePublicKey.trim(),
            createdAt = now
        )

        transaction {
            AccountsTable.insert {
                it[AccountsTable.id] = record.id
                it[AccountsTable.role] = record.role
                it[AccountsTable.name] = record.name
                it[AccountsTable.dob] = record.dob
                it[AccountsTable.sex] = record.sex
                it[AccountsTable.heightCm] = record.heightCm
                it[AccountsTable.weightKg] = record.weightKg
                it[AccountsTable.district] = record.district
                it[AccountsTable.phone] = record.phone
                it[AccountsTable.guardianConsent] = record.guardianConsent
                it[AccountsTable.photoPath] = record.photoPath
                it[AccountsTable.devicePubkey] = record.devicePubkey
                it[AccountsTable.createdAt] = record.createdAt
            }
        }

        return record
    }

    fun createCoach(
        id: String = nextCoachId(),
        name: String,
        district: String? = null,
        pin: String
    ): AccountRecord {
        val salt = generateSalt()
        val pinHash = hashPin(pin, salt)
        val now = System.currentTimeMillis()

        val record = AccountRecord(
            id = id,
            role = "COACH",
            name = name.trim(),
            district = district?.trim(),
            pinHash = pinHash,
            pinSalt = salt,
            createdAt = now
        )

        transaction {
            AccountsTable.insert {
                it[AccountsTable.id] = record.id
                it[role] = record.role
                it[AccountsTable.name] = record.name
                it[AccountsTable.district] = record.district
                it[AccountsTable.pinHash] = record.pinHash
                it[AccountsTable.pinSalt] = record.pinSalt
                it[createdAt] = record.createdAt
            }
        }

        return record
    }

    fun authenticateCoach(
        coachId: String,
        pin: String,
        devicePublicKey: String
    ): AccountRecord? {
        val account = getAccount(coachId) ?: return null
        if (account.role != "COACH" || account.pinHash == null || account.pinSalt == null) {
            return null
        }

        val computedHash = hashPin(pin, account.pinSalt)
        if (!MessageDigest.isEqual(computedHash.toByteArray(), account.pinHash.toByteArray())) {
            return null
        }

        // Bind coach's device public key on sign-in
        transaction {
            AccountsTable.update({ AccountsTable.id eq coachId }) {
                it[devicePubkey] = devicePublicKey
            }
        }

        return account.copy(devicePubkey = devicePublicKey)
    }

    fun getAccount(id: String): AccountRecord? = transaction {
        AccountsTable.selectAll().where { AccountsTable.id eq id }.firstOrNull()?.let { row ->
            AccountRecord(
                id = row[AccountsTable.id],
                role = row[AccountsTable.role],
                name = row[AccountsTable.name],
                dob = row[AccountsTable.dob],
                sex = row[AccountsTable.sex],
                heightCm = row[AccountsTable.heightCm],
                weightKg = row[AccountsTable.weightKg],
                district = row[AccountsTable.district],
                phone = row[AccountsTable.phone],
                guardianConsent = row[AccountsTable.guardianConsent],
                photoPath = row[AccountsTable.photoPath],
                pinHash = row[AccountsTable.pinHash],
                pinSalt = row[AccountsTable.pinSalt],
                devicePubkey = row[AccountsTable.devicePubkey],
                createdAt = row[AccountsTable.createdAt]
            )
        }
    }

    fun getAllAthletes(): List<AccountRecord> = transaction {
        AccountsTable.selectAll().where { AccountsTable.role eq "ATHLETE" }.map { row ->
            AccountRecord(
                id = row[AccountsTable.id],
                role = row[AccountsTable.role],
                name = row[AccountsTable.name],
                dob = row[AccountsTable.dob],
                sex = row[AccountsTable.sex],
                heightCm = row[AccountsTable.heightCm],
                weightKg = row[AccountsTable.weightKg],
                district = row[AccountsTable.district],
                phone = row[AccountsTable.phone],
                guardianConsent = row[AccountsTable.guardianConsent],
                photoPath = row[AccountsTable.photoPath],
                pinHash = row[AccountsTable.pinHash],
                pinSalt = row[AccountsTable.pinSalt],
                devicePubkey = row[AccountsTable.devicePubkey],
                createdAt = row[AccountsTable.createdAt]
            )
        }
    }

    fun getAllCoaches(): List<AccountRecord> = transaction {
        AccountsTable.selectAll().where { AccountsTable.role eq "COACH" }.map { row ->
            AccountRecord(
                id = row[AccountsTable.id],
                role = row[AccountsTable.role],
                name = row[AccountsTable.name],
                dob = row[AccountsTable.dob],
                sex = row[AccountsTable.sex],
                heightCm = row[AccountsTable.heightCm],
                weightKg = row[AccountsTable.weightKg],
                district = row[AccountsTable.district],
                phone = row[AccountsTable.phone],
                guardianConsent = row[AccountsTable.guardianConsent],
                photoPath = row[AccountsTable.photoPath],
                pinHash = row[AccountsTable.pinHash],
                pinSalt = row[AccountsTable.pinSalt],
                devicePubkey = row[AccountsTable.devicePubkey],
                createdAt = row[AccountsTable.createdAt]
            )
        }
    }

    fun seedCoachesIfEmpty(seedFile: File) {
        if (!seedFile.exists()) return
        val existingCoaches = getAllCoaches()
        if (existingCoaches.isNotEmpty()) return

        try {
            val seedText = seedFile.readText()
            val coaches = json.decodeFromString<List<SeedCoach>>(seedText)
            for (coach in coaches) {
                createCoach(
                    id = coach.id,
                    name = coach.name,
                    district = coach.district,
                    pin = coach.pin
                )
            }
        } catch (e: Exception) {
            println("Failed to seed coaches: ${e.message}")
        }
    }
}
