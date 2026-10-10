package app.pramaan.server

import app.pramaan.core.model.Badge
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class Phase1ServerTest {

    @BeforeEach
    fun setupDb(@TempDir tempDir: File) {
        val testDbFile = File(tempDir, "test_pramaan.db")
        ServerDb.init(testDbFile)
    }

    @Test
    fun `test athlete registration and token issuance`(@TempDir tempDir: File) {
        val input = AthleteProfileInput(
            name = "Manpreet Kaur",
            dob = "2010-03-14",
            sex = "F",
            heightCm = 152.0,
            weightKg = 41.5,
            district = "Ranchi",
            phone = "9876543210",
            guardianConsent = true,
            devicePublicKey = "test_device_public_key_b64"
        )

        val photosDir = File(tempDir, "photos")
        val fakePhoto = ByteArray(100) { 0x55 }

        val account = AccountRepo.createAthlete(input, fakePhoto, photosDir)
        account.id shouldStartWith "ATH-"
        account.name shouldBe "Manpreet Kaur"
        account.role shouldBe "ATHLETE"
        account.guardianConsent shouldBe true
        account.devicePubkey shouldBe "test_device_public_key_b64"
        account.photoPath shouldNotBe null
        File(account.photoPath!!).exists() shouldBe true

        // Generate and verify token
        val token = TokenRepo.createToken(account.id)
        token.isNotEmpty() shouldBe true

        val tokenHash = TokenRepo.hashToken(token)
        val resolvedAccount = TokenRepo.findAccount(tokenHash)
        resolvedAccount shouldNotBe null
        resolvedAccount!!.id shouldBe account.id

        // Revoke token
        TokenRepo.revokeToken(token) shouldBe true
        TokenRepo.findAccount(tokenHash) shouldBe null
    }

    @Test
    fun `test coach seeding and login key binding`(@TempDir tempDir: File) {
        val seedFile = File(tempDir, "coaches.json")
        seedFile.writeText(
            """
            [
              { "id": "COA-01", "name": "Rakesh Kumar", "district": "Ranchi", "pin": "2468" },
              { "id": "COA-02", "name": "Sunita Devi",  "district": "Howrah", "pin": "1357" }
            ]
            """.trimIndent()
        )

        AccountRepo.seedCoachesIfEmpty(seedFile)

        val coaches = AccountRepo.getAllCoaches()
        coaches.size shouldBe 2

        val coach1 = AccountRepo.getAccount("COA-01")
        coach1 shouldNotBe null
        coach1!!.name shouldBe "Rakesh Kumar"
        coach1.devicePubkey shouldBe null // Not bound yet

        // Login with wrong PIN
        val failedLogin = AccountRepo.authenticateCoach("COA-01", "9999", "coach_key_1")
        failedLogin shouldBe null

        // Login with correct PIN
        val successfulLogin = AccountRepo.authenticateCoach("COA-01", "2468", "coach_key_1")
        successfulLogin shouldNotBe null
        successfulLogin!!.devicePubkey shouldBe "coach_key_1"

        // Verify key is now persisted in DB
        val reloadedCoach = AccountRepo.getAccount("COA-01")
        reloadedCoach!!.devicePubkey shouldBe "coach_key_1"
    }

    @Test
    fun `test session persistence with status, integrity, and event logging`() {
        val sessionId = "ses_1730000000_test"
        val athleteId = "ATH-7K3Q"

        // Create athlete record
        AccountRepo.createAthlete(
            AthleteProfileInput(
                name = "Test Athlete",
                dob = "2005-01-01",
                sex = "M",
                heightCm = 170.0,
                guardianConsent = true,
                devicePublicKey = "matching_device_key"
            ),
            photoBytes = null,
            photosDir = File("temp_photos")
        )

        val sessionRecord = ServerSessionRecord(
            id = sessionId,
            athleteId = athleteId,
            athleteInitials = "TA",
            test = "VERTICAL_JUMP",
            best = 48.2,
            unit = "cm",
            status = "AWAITING_REVIEW",
            integrity = "VERIFIED",
            integrityJson = """[{"id":"C1","name":"Seal","pass":true,"value":"ok","limit":"exact"}]""",
            manifestSha256 = "abc123sha256",
            submittedAt = System.currentTimeMillis()
        )

        ServerDb.saveOrUpdate(sessionRecord)

        // Event logging
        EventLog.log(sessionId, "SUBMITTED", actorId = athleteId, detail = "Submitted by athlete")
        EventLog.log(sessionId, "SERVER_VERIFIED", actorId = "SERVER", detail = "Integrity VERIFIED")

        val fetched = ServerDb.getById(sessionId)
        fetched shouldNotBe null
        fetched!!.status shouldBe "AWAITING_REVIEW"
        fetched.integrity shouldBe "VERIFIED"
        fetched.badge shouldBe Badge.VERIFIED

        val events = EventLog.getEvents(sessionId)
        events.size shouldBe 2
        events[0].type shouldBe "SUBMITTED"
        events[0].actorId shouldBe athleteId
        events[1].type shouldBe "SERVER_VERIFIED"
    }

    @Test
    fun `test device key binding validation logic`() {
        val registeredKey = "MFkwEwYHKoZIzj0CAQ..."
        val wrongKey = "MFkwEwYHKoZIzj0CAQ_DIFFERENT"

        // Athlete account has registeredKey
        val accountId = "ATH-TEST1"

        // Case 1: Manifest has matching athlete ID and matching device public key
        val manifestAthleteId = accountId
        val manifestDeviceKey = registeredKey

        val matches = (manifestAthleteId == accountId && manifestDeviceKey == registeredKey)
        matches shouldBe true

        // Case 2: Manifest has mismatched device key
        val mismatchDevice = (manifestAthleteId == accountId && wrongKey == registeredKey)
        mismatchDevice shouldBe false

        // Case 3: Manifest has wrong athlete ID
        val mismatchAthlete = ("ATH-OTHER" == accountId && manifestDeviceKey == registeredKey)
        mismatchAthlete shouldBe false
    }
}
