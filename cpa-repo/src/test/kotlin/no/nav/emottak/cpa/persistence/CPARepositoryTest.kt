package no.nav.emottak.cpa.persistence

import no.nav.emottak.cpa.databasetest.PostgresTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `CPA db entry bør ha timestamps`() {
        val cpaRepository = CPARepository(postgres)
        assertEquals(getPostgresTimestamp(), cpaRepository.findCpaEntry(CPA_ID)?.updatedDate)
    }

    @Test
    fun `CPA db entry bør ha partner HER ID`() {
        val cpaRepository = CPARepository(postgres)
        assertEquals("8141253", cpaRepository.findCpaEntry(CPA_ID)?.herId)
    }
}
