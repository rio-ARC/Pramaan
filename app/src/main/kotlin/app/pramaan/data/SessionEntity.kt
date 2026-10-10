package app.pramaan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val athleteId: String,
    val test: String,
    val source: String,
    val folder: String,
    val best: Double,
    val unit: String,
    val badge: String,
    val synced: Boolean = false,
    val serverBadge: String? = null,
    val certificateJws: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
