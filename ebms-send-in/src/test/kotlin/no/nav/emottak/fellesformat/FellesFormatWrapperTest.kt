package no.nav.emottak.fellesformat

import no.nav.emottak.ebms.log
import no.nav.emottak.validSendInRequest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class FellesFormatWrapperTest {

    @Test
    fun wrapMessageInEIFellesFormat() {
        val sendInRequest = validSendInRequest.value
        val fellesFormat = wrapMessageInEIFellesFormat(sendInRequest)
        Assertions.assertEquals(fellesFormat.mottakenhetBlokk.partnerReferanse, sendInRequest.cpaId)
        log.info(marshal(fellesFormat))
    }
}
