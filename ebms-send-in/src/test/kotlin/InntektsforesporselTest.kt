import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListe
import no.nav.emottak.utbetaling.unmarshal
import org.junit.jupiter.api.Test

class InntektsforesporselTest {

    @Test
    fun unmarshalMsgHead() {
        val response = unmarshal(
            String(
                this::class.java.classLoader.getResourceAsStream("inntektsforesporsel/inntektsforesporsel.xml")!!
                    .readAllBytes()
            ),
            MsgHead::class.java
        )

        val request = response.document.map { doc -> doc.refDoc.content.any }.first().first()
        assert(request is FinnUtbetalingListe)
    }
}
