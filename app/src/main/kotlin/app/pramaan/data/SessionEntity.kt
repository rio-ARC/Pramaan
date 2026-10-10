package app.pramaan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val ownerAccountId: String,
    val test: String,
    val source: String,
    val folder: String,
    val best: Double,
    val unit: String,
    val localIntegrity: String,
    val status: String,
    val serverIntegrity: String? = null,
    val reviewedByName: String? = null,
    val reviewMethod: String? = null,
    val reviewReason: String? = null,
    val certificateJws: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
