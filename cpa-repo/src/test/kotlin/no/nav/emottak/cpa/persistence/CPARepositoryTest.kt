package no.nav.emottak.cpa.persistence

import kotlin.test.Test
import kotlin.test.assertEquals


class CPARepositoryTest : DBTest() {

    val CPA_ID = "nav:qass:35065"
    @Test
    fun `Bør finne CPA i databasen`() {
        val cpaRepository = CPARepository(this.db)
        cpaRepository.findCpa(CPA_ID).also {
            assertEquals(CPA_ID,it?.cpaid)
        }
    }

    @Test
    fun `CPA db entry bør ha timestamps`() {
        val cpaRepository = CPARepository(this.db)
        assertEquals(DEFAULT_TIMESTAMP, cpaRepository.findCpaEntry(CPA_ID)?.activeSince)
    }

}