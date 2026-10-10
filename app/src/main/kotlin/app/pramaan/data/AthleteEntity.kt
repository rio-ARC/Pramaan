package app.pramaan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "athletes")
data class AthleteEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val initials: String,
    val age: Int,
    val sex: String,
    val heightCm: Double,
    val weightKg: Double,
    val guardianConsent: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)
