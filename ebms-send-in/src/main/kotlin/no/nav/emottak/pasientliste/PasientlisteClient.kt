package no.nav.emottak.pasientliste

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.basicAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.log
import no.nav.emottak.fellesformat.FellesFormatXmlMarshaller
import no.nav.emottak.util.getEnvVar
import no.nav.emottak.util.isProdEnv
import no.trygdeetaten.xml.eiff._1.EIFellesformat
import java.io.FileInputStream

object PasientlisteClient {
    private val url = getEnvVar("PASIENTLISTE_URL", "https://wasapp-q1.adeo.no/nav-emottak-practitioner-web/remoting/httpreqhandler-practitioner")
    private val username = lazy { String(FileInputStream("/secret/serviceuser/username").readAllBytes()) }
    private val password = lazy { String(FileInputStream("/secret/serviceuser/password").readAllBytes()) }

    fun hentPasientliste(request: EIFellesformat): EIFellesformat {
        val marshalledFellesformat = FellesFormatXmlMarshaller.marshal(request)
        val httpClient = HttpClient(CIO)

        if (!isProdEnv()) {
            log.info("Sending in HentPasientliste request with body: $marshalledFellesformat")
        }

        val result = runBlocking {
            try {
                httpClient.post(url) {
                    setBody(marshalledFellesformat)
                    contentType(ContentType.Application.Xml)
                    basicAuth(username.value, password.value)
                }.bodyAsText()
            } catch (e: Exception) {
                log.error("PasientlisteForesporsel error", e)
                throw e
            } finally {
                httpClient.close()
            }
        }

        if (!isProdEnv()) {
            log.info("HentPasientliste result: {}", result)
        }

        return FellesFormatXmlMarshaller.unmarshal(result, EIFellesformat::class.java)
    }
}
