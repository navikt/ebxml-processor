package no.nav.emottak.cpa

import no.nav.emottak.cpa.feil.CpaValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CPAValidatorKtTest {
    @Test
    fun `Valider gyldig header mot CPA`() {
        val cpa = TestUtil.createValidTestCPA()
        val header = createValidTestHeader()
        cpa.validate(header)
    }

    @Test
    fun `Header har ugyldig fra role`() {
        val cpa = TestUtil.createValidTestCPA()
        val header = createValidTestHeader().copy(
            from = createValidFromHERParty().copy(
                role = "KontrollUtbetaler"
            )
        )
        val exception = assertThrows<CpaValidationException> {
            cpa.validate(header)
        }
        assertEquals("Role ${header.from.role} matcher ikke party", exception.message)
    }

    @Test
    fun `Header har gyldig fra role men ugyldig til role`() {
        val cpa = TestUtil.createValidTestCPA()
        val header = createValidTestHeader().copy(
            to = createValidToHERParty().copy(
                role = "Behandler"
            )
        )
        val exception = assertThrows<CpaValidationException> {
            cpa.validate(header)
        }
        assertEquals("Role ${header.to.role} matcher ikke party", exception.message)
    }

    @Test
    fun `Role og service matcher, men ikke action`() {
        val cpa = TestUtil.createValidTestCPA()
        val header = createValidTestHeader().copy(
            action = "InvalidAction"
        )
        val exception = assertThrows<CpaValidationException> {
            cpa.validate(header)
        }
        assertEquals("Action ${header.action} matcher ikke service ${header.service}", exception.message)
    }

    @Test
    fun `CPA ID i header matcher ikke CPA ID fra CPA`() {
        val cpa = TestUtil.createValidTestCPA()
        val header = createValidTestHeader().copy(
            cpaId = "InvalidCPAID"
        )
        val exception = assertThrows<CpaValidationException> {
            cpa.validate(header)
        }
        assertEquals("Funnet CPA (ID: ${cpa.cpaid}) matcher ikke cpaid til melding: ${header.cpaId}", exception.message)
    }
}
