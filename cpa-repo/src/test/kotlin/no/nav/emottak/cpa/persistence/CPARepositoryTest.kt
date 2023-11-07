package no.nav.emottak.cpa.persistence

import kotlin.test.Test
import kotlin.test.assertEquals


class CPARepositoryTest : DBTest() {

    val CPA_ID = "nav:qass:35065"

    val cpaRepository = CPARepository()
    @Test
    fun `Bør finne CPA i databasen`() {
            cpaRepository.findCpa(CPA_ID).also {
                assertEquals(CPA_ID,it?.cpaid)
            }
    }

}