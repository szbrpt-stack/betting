package com.s2s.sigma.storage

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPersistenceTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun favoriteAndAnalysisPersist() = runBlocking {
        val dao = database.sigmaDao()
        dao.upsertFavorite(FavoriteEntity("event", "demo", "2026-11-17T12:00:00-05:00"))
        assertEquals("demo", dao.observeFavorites().first().single().referenceId)

        val row = AnalysisEntity("a1", "e1", "q1", "DESCRIPTIVE", "2026-11-17T12:00:00-05:00", "2026-11-17T12:01:00-05:00", "{}")
        dao.upsertAnalysis(row)
        assertEquals(row, dao.analysis("a1"))
    }
}
