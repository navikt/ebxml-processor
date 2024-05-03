package no.nav.emottak.cpa

import no.nav.emottak.cpa.feil.CpaValidationException
import no.nav.emottak.cpa.validation.validate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CPAValidatorKtTest {
    @Test
    fun `Valider gyldig header mot CPA`() {
        val cpa = TestUtil.createValidTestCPA()
        val validationRequest = createValidValidationRequest()
        cpa.validate(validationRequest)
    }

    @Test
    fun `Header har ugyldig fra role`() {
        val cpa = TestUtil.createValidTestCPA()
        val partyName = cpa.partyInfo[0].partyName
        val validationRequest = createValidValidationRequest().let {
            it.copy(
                addressing = it.addressing.copy(
                    from = createValidFromHERParty().copy(
                        role = "KontrollUtbetaler"
                    )
                )
            )
        }
        val exception = assertThrows<CpaValidationException> {
            cpa.validate(validationRequest)
        }
        assertEquals(
            "Role ${validationRequest.addressing.from.role} matcher ikke service ${validationRequest.addressing.service} for party $partyName",
            exception.message
        )
    }

    @Test
    fun `Header har gyldig fra role men ugyldig til role`() {
        val cpa = TestUtil.createValidTestCPA()
        val partyName = cpa.partyInfo[1].partyName
        val validationRequest = createValidValidationRequest().let {
            it.copy(
                addressing = it.addressing.copy(
                    to = createValidToHERParty().copy(
                        role = "Behandler"
                    )
                )
            )
        }
        val exception = assertThrows<CpaValidationException> {
            cpa.validate(validationRequest)
        }
        assertEquals(
            "Role ${validationRequest.addressing.to.role} matcher ikke service ${validationRequest.addressing.service} for party $partyName",
            exception.message
        )
    }

    @Test
    fun `Role og service matcher, men ikke action`() {
        val cpa = TestUtil.createValidTestCPA()
        val partyName = cpa.partyInfo[0].partyName
        val validationRequest = createValidValidationRequest().let {
            it.copy(
                addressing = it.addressing.copy(
                    action = "InvalidAction"
                )
            )
        }
        val exception = assertThrows<CpaValidationException> {
            cpa.validate(validationRequest)
        }
        assertEquals(
            "Action ${validationRequest.addressing.action} matcher ikke service ${validationRequest.addressing.service} for sending party $partyName",
            exception.message
        )
    }

    @Test
    fun `CPA ID i validationRequest matcher ikke CPA ID fra CPA`() {
        val cpa = TestUtil.createValidTestCPA()
        val validationRequest = createValidValidationRequest().copy(
            cpaId = "InvalidCPAID"
        )
        val exception = assertThrows<CpaValidationException> {
            cpa.validate(validationRequest)
        }
        assertEquals(
            "Funnet CPA (ID: ${cpa.cpaid}) matcher ikke cpaid til melding: ${validationRequest.cpaId}",
            exception.message
        )
    }
}
