import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.feil.v1.BrukerIkkeFunnet
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.feil.v1.ObjectFactory
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListe
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListeBrukerIkkeFunnet
import no.nav.emottak.utbetaling.unmarshal
import no.nav.emottak.utbetaling.utbetalingXmlMarshaller
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberFunctions

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

    @Test
    fun canMarshalFaultInfo() {
        val stelvioFault = ObjectFactory().createBrukerIkkeFunnet()
        stelvioFault.errorMessage = "Ayylmaoo"
        val brukerIkkeFunnetException: Exception = FinnUtbetalingListeBrukerIkkeFunnet("Ayylmaoo", stelvioFault)
        val createFinnBrukersUtbetalteYtelserbrukerIkkeFunnet =
            no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.ObjectFactory()
                .createFinnBrukersUtbetalteYtelserbrukerIkkeFunnet(
                    with(brukerIkkeFunnetException) {
                        this::class.memberFunctions
                            .find { func -> func.name == "getFaultInfo" }!!
                            .call(this) as BrukerIkkeFunnet
                    }
                )

        println(
            String(
                utbetalingXmlMarshaller.marshalToByteArray(createFinnBrukersUtbetalteYtelserbrukerIkkeFunnet)
            )
        )
    }
}
