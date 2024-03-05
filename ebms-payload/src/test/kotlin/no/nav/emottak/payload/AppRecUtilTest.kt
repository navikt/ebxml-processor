package no.nav.emottak.payload

import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.emottak.melding.apprec.AppRecErrorCode
import no.nav.emottak.melding.apprec.createNegativeApprec
import no.nav.emottak.payload.util.marshal
import no.nav.emottak.payload.util.unmarshal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppRecUtilTest {

    @Test
    fun `Create negative AppRec from exception`() {
        val msgHead = unmarshal(String(this::class.java.classLoader.getResource("xml/egenandelforesporsel.xml")!!.readBytes()), MsgHead::class.java)
        val originalMsgId = msgHead.msgInfo.msgId
        val originalSender = msgHead.msgInfo.sender.organisation.ident.first().id
        val originalReceiver = "889640782"
        val exception = RuntimeException("Oh no!")
        val apprec = createNegativeApprec(msgHead, exception)

        assertEquals(originalSender, apprec.receiver.hcp.inst.id)
        assertEquals(originalReceiver, apprec.sender.hcp.inst.id)
        assertEquals(originalMsgId, apprec.originalMsgId.id)
        assertEquals("2", apprec.status.v)
        assertEquals(AppRecErrorCode.X99.name, apprec.error.first().v)
        assertEquals("Oh no!", apprec.error.first().ot)

        print(marshal(apprec))
    }
}
