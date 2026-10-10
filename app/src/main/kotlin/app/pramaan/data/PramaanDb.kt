package app.pramaan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AthleteEntity::class, SessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PramaanDb : RoomDatabase() {
    abstract fun athleteDao(): AthleteDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: PramaanDb? = null

        fun getDatabase(context: Context): PramaanDb {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PramaanDb::class.java,
                    "pramaan_app.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
