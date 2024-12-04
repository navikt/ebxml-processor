package no.nav.emottak.cpa.persistence

import no.nav.emottak.cpa.databasetest.PostgresTest
import no.nav.emottak.cpa.feil.CpaValidationException
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun `CPA db entry bør ha timestamps`() {
        val cpaRepository = CPARepository(postgres)
        assertEquals(getPostgresTimestamp(), cpaRepository.findCpaEntry(CPA_ID)?.updatedDate)
    }

    @Test
    fun `CPA db entry bør ha partner HER ID`() {
        val cpaRepository = CPARepository(postgres)
        assertEquals("8141253", cpaRepository.findCpaEntry(CPA_ID)?.herId)
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
}
