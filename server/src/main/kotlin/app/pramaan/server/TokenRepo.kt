package app.pramaan.server

import app.pramaan.core.crypto.Sha256
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.security.SecureRandom
import java.util.Base64

object TokenRepo {
    private val random = SecureRandom()

    fun hashToken(token: String): String {
        val digest = Sha256.digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun createToken(accountId: String): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val tokenHash = hashToken(token)
        val now = System.currentTimeMillis()

        transaction {
            TokensTable.insert {
                it[TokensTable.tokenHash] = tokenHash
                it[TokensTable.accountId] = accountId
                it[TokensTable.createdAt] = now
            }
        }

        return token
    }

    fun findAccount(tokenHash: String): AccountRecord? {
        val accountId = transaction {
            TokensTable.selectAll()
                .where { TokensTable.tokenHash eq tokenHash }
                .firstOrNull()
                ?.get(TokensTable.accountId)
        } ?: return null

        return AccountRepo.getAccount(accountId)
    }

    fun revokeToken(token: String): Boolean {
        val tokenHash = hashToken(token)
        return transaction {
            TokensTable.deleteWhere { TokensTable.tokenHash eq tokenHash } > 0
        }
    }
}
