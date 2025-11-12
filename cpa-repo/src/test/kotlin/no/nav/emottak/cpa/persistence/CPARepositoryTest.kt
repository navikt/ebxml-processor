package no.nav.emottak.cpa.persistence

import no.nav.emottak.cpa.databasetest.PostgresTest
import no.nav.emottak.cpa.feil.CpaValidationException
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CPARepositoryTest : PostgresTest() {

    val CPA_ID = "nav:qass:35065"

    @Test
    fun `Bør finne CPA i databasen`() {
        val cpaRepository = CPARepository(postgres)
        cpaRepository.findCpa(CPA_ID).also {
            assertEquals(CPA_ID, it?.cpaid)
        }
    }

    @Test
    fun `CPA db entry bør ha updated-timestamps`() {
        val cpaRepository = CPARepository(postgres)
        assertEquals(getPostgresTimestamp(), cpaRepository.findCpaEntry(CPA_ID)?.updatedDate)
    }

    @Test
    fun `CPA db entry bør ha partner HER ID`() {
        val cpaRepository = CPARepository(postgres)
        assertEquals("8141253", cpaRepository.findCpaEntry(CPA_ID)?.herId)
    }

    @Test
    fun `CPA db entry har lastUsed-timestamp som null`() {
        val cpaRepository = CPARepository(postgres)
        assertNull(cpaRepository.findCpaEntry(CPA_ID)?.lastUsed) // Null første gangen
    }

    @Test
    fun `CPA db entry har lastUsed-timestamp etter updateCpaLastUsed()`() {
        val cpaRepository = CPARepository(postgres)
        cpaRepository.updateCpaLastUsed(CPA_ID)
        assertNotNull(cpaRepository.findCpaEntry(CPA_ID)?.lastUsed)
    }

    @Test
    fun `Hent process config med gyldig role service action kombo`() {
        val cpaRepository = CPARepository(postgres)
        val processConfig = cpaRepository.getProcessConfig(
            "Behandler",
            "HarBorgerFrikort",
            "EgenandelForesporsel"
        )
        assertNotNull(processConfig)
        assertTrue(processConfig.apprec)
    }

    @Test
    fun `Hent process config med ugyldig role service action kombo`() {
        val cpaRepository = CPARepository(postgres)
        assertThrows<CpaValidationException> {
            cpaRepository.getProcessConfig(
                "Utleverer",
                "HarBorgerFrikort",
                "EgenandelForesporsel"
            )
        }
    }

    @Test
    fun `Hent timestamp for nyligst oppdaterte CPA`() {
        val cpaRepository = CPARepository(postgres)
        assertEquals(getPostgresTimestamp().toString(), cpaRepository.findTimestampCpaLatestUpdated())
    }

    @Test
    fun `Hent timestamps for alle CPA'er for når de ble sist oppdatert`() {
        val cpaRepository = CPARepository(postgres)
        val map = cpaRepository.findTimestampsCpaUpdated(emptyList())
        assertEquals(3, map.size)
        for (entry in map) {
            assertEquals(getPostgresTimestamp().toString(), entry.value)
        }
    }

    @Test
    fun `Hent timestamps for utvalgte CPA'er for når de ble sist oppdatert`() {
        val cpaRepository = CPARepository(postgres)
        val map = cpaRepository.findTimestampsCpaUpdated(listOf(CPA_ID))
        assertEquals(1, map.size)
        assertEquals(getPostgresTimestamp().toString(), map[CPA_ID])
    }

    @Test
    fun `Hent timestamps for alle CPA'er for når de ble sist brukt`() {
        val cpaRepository = CPARepository(postgres)
        cpaRepository.updateCpaLastUsed(CPA_ID)
        cpaRepository.updateCpaLastUsed("multiple_channels_and_multiple_endpoints")
        val map = cpaRepository.findTimestampsCpaLastUsed()
        assertEquals(3, map.size)
        assertNotNull(map[CPA_ID])
        assertNull(map["nav-qass-31162"]) // Aldri brukt
    }
}
