package com.s2s.sigma.storage

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.s2s.sigma.data.*
import com.s2s.sigma.domain.SigmaEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportPersistenceTest {
    @Test
    fun importedDocumentSurvivesDatabaseReopenWithQuestionsAndObservations() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "import-persistence-${System.nanoTime()}.db"
        val event = SportEvent(
            "event-import", "basketball", "nba", "A vs B",
            listOf(Participant("a", "A", ParticipantKind.TEAM), Participant("b", "B", ParticipantKind.TEAM)),
            "2026-11-19T20:00:00-05:00", "America/Bogota", status = EventStatus.SCHEDULED,
            format = EventFormat.TEAM, retrievedAt = "2026-11-18T08:00:00-05:00", isDemo = false,
        )
        val question = SigmaQuestion(
            "question-import", event.id, "Puntos previos",
            TargetDefinition("team_points", "points", "3 juegos", "Media descriptiva"),
        )
        val request = ResearchRequest(
            "request-import", event.id, question, "2026-11-18T09:00:00-05:00",
            "2026-11-18T09:00:00-05:00", event,
        )
        val source = EvidenceSource(
            "source-import", "Fuente", "https://example.com/source", consultedAt = request.cutoffAt,
            cutoffAt = request.cutoffAt, isDemo = false,
        )
        val observations = listOf(100.0, 110.0, 120.0).mapIndexed { index, value ->
            Observation(
                "observation-$index", event.id, "a", "team_points", value, "points", "game-$index",
                "2026-11-${10 + index}T20:00:00-05:00", source.id, request.cutoffAt, request.cutoffAt,
            )
        }
        val response = ResearchResponse(
            request.id, observations, listOf(source), emptyList(), request.cutoffAt, isDemo = false,
        )
        val document = ResearchImportDocument(
            1, "document-import", ImportProvenance("test", request.cutoffAt), event,
            listOf(ImportedQuestionResearch(request, response)),
        )

        var database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var repository = SigmaRepository(database.sigmaDao())
        repository.saveImport(document, "test.json", request.cutoffAt)
        repository.save(request, SigmaEngine.analyze(request, response))
        database.close()

        database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        repository = SigmaRepository(database.sigmaDao())
        val stored = database.sigmaDao().observeImportDocuments().first().single()
        val decoded = repository.decodeImport(stored)
        val analysis = repository.decodeAnalysis(database.sigmaDao().observeAnalyses().first().single())
        assertNotNull(decoded)
        assertNotNull(analysis)
        assertEquals(event.id, decoded!!.event.id)
        assertEquals(question.id, decoded.investigations.single().request.question.id)
        assertEquals(3, decoded.investigations.single().response.observations.size)
        assertEquals(110.0, analysis!!.statistics.mean!!, 0.0001)
        database.close()
        context.deleteDatabase(name)
    }
}
