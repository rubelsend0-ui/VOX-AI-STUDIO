package com.voxai.studio.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_voices")
data class CachedVoiceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val provider: String,
    val status: String,
    val durationSeconds: Double,
    val createdAt: String
)

@Entity(tableName = "cached_generations")
data class CachedGenerationEntity(
    @PrimaryKey val id: String,
    val voiceId: String,
    val voiceName: String,
    val projectTitle: String,
    val scriptText: String,
    val language: String,
    val status: String,
    val progress: Int,
    val durationSeconds: Double,
    val mp3Url: String?,
    val wavUrl: String?,
    val createdAt: String
)

@Dao
interface StudioDao {
    @Query("SELECT * FROM cached_voices ORDER BY createdAt DESC")
    fun observeVoices(): Flow<List<CachedVoiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoices(voices: List<CachedVoiceEntity>)

    @Query("DELETE FROM cached_voices")
    suspend fun clearVoices()

    @Query("SELECT * FROM cached_generations ORDER BY createdAt DESC")
    fun observeGenerations(): Flow<List<CachedGenerationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenerations(items: List<CachedGenerationEntity>)

    @Query("DELETE FROM cached_generations")
    suspend fun clearGenerations()
}

@Database(
    entities = [CachedVoiceEntity::class, CachedGenerationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VoxDatabase : RoomDatabase() {
    abstract fun studioDao(): StudioDao

    companion object {
        @Volatile private var INSTANCE: VoxDatabase? = null

        fun getInstance(context: Context): VoxDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VoxDatabase::class.java,
                    "vox_ai_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
