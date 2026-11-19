package com.s2s.sigma.storage

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "competitions")
data class CompetitionEntity(
    @PrimaryKey val id: String,
    val sportId: String,
    val name: String,
    val region: String?,
    val season: String?,
    val updatedAt: String,
)

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val sportId: String,
    val competitionId: String,
    val name: String,
    val startsAt: String,
    val status: String,
    val payloadJson: String,
    val consultedAt: String,
)

@Entity(tableName = "favorites", primaryKeys = ["kind", "referenceId"])
data class FavoriteEntity(
    val kind: String,
    val referenceId: String,
    val createdAt: String,
)

@Entity(tableName = "research_requests")
data class ResearchEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val questionId: String,
    val requestJson: String,
    val requestedAt: String,
)

@Entity(tableName = "evidence_sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val researchId: String,
    val name: String,
    val url: String,
    val consultedAt: String,
    val cutoffAt: String,
    val payloadJson: String,
)

@Entity(tableName = "analysis_results")
data class AnalysisEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val questionId: String,
    val verdict: String,
    val cutoffAt: String,
    val createdAt: String,
    val payloadJson: String,
)

@Entity(tableName = "daily_cache", primaryKeys = ["cacheKey", "localDate"])
data class DailyCacheEntity(
    val cacheKey: String,
    val localDate: String,
    val queriedAt: String,
    val expiresAt: String,
    val payloadJson: String,
)

@Entity(tableName = "import_documents")
data class ImportDocumentEntity(
    @PrimaryKey val documentId: String,
    val schemaVersion: Int,
    val eventId: String,
    val importedAt: String,
    val fileName: String,
    val provenanceJson: String,
    val payloadJson: String,
)

@Entity(tableName = "imported_questions")
data class ImportedQuestionEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val eventId: String,
    val payloadJson: String,
)

@Entity(tableName = "imported_observations")
data class ImportedObservationEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val researchId: String,
    val eventId: String,
    val sourceId: String,
    val payloadJson: String,
)

@Dao
interface SigmaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompetitions(items: List<CompetitionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(items: List<EventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE kind = :kind AND referenceId = :referenceId")
    suspend fun deleteFavorite(kind: String, referenceId: String)

    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResearch(item: ResearchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSources(items: List<SourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnalysis(item: AnalysisEntity)

    @Query("SELECT * FROM analysis_results ORDER BY createdAt DESC")
    fun observeAnalyses(): Flow<List<AnalysisEntity>>

    @Query("SELECT * FROM analysis_results WHERE id = :id")
    suspend fun analysis(id: String): AnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCache(item: DailyCacheEntity)

    @Query("SELECT * FROM daily_cache WHERE cacheKey = :key AND localDate = :localDate LIMIT 1")
    suspend fun cache(key: String, localDate: String): DailyCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportDocument(item: ImportDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportedQuestions(items: List<ImportedQuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportedObservations(items: List<ImportedObservationEntity>)

    @Query("SELECT * FROM import_documents ORDER BY importedAt DESC")
    fun observeImportDocuments(): Flow<List<ImportDocumentEntity>>

    @Transaction
    suspend fun saveImportedResearch(
        document: ImportDocumentEntity,
        event: EventEntity,
        questions: List<ImportedQuestionEntity>,
        research: List<ResearchEntity>,
        sources: List<SourceEntity>,
        observations: List<ImportedObservationEntity>,
    ) {
        upsertImportDocument(document)
        upsertEvents(listOf(event))
        upsertImportedQuestions(questions)
        research.forEach { upsertResearch(it) }
        upsertSources(sources)
        upsertImportedObservations(observations)
    }
}

@Database(
    entities = [
        CompetitionEntity::class,
        EventEntity::class,
        FavoriteEntity::class,
        ResearchEntity::class,
        SourceEntity::class,
        AnalysisEntity::class,
        DailyCacheEntity::class,
        ImportDocumentEntity::class,
        ImportedQuestionEntity::class,
        ImportedObservationEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sigmaDao(): SigmaDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `import_documents` (`documentId` TEXT NOT NULL, `schemaVersion` INTEGER NOT NULL, `eventId` TEXT NOT NULL, `importedAt` TEXT NOT NULL, `fileName` TEXT NOT NULL, `provenanceJson` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, PRIMARY KEY(`documentId`))""",
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `imported_questions` (`id` TEXT NOT NULL, `documentId` TEXT NOT NULL, `eventId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, PRIMARY KEY(`id`))""",
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `imported_observations` (`id` TEXT NOT NULL, `documentId` TEXT NOT NULL, `researchId` TEXT NOT NULL, `eventId` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, PRIMARY KEY(`id`))""",
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "s2s-sigma.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
