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
import no.nav.emottak.fellesformat.wrapMessageInEIFellesFormat
import no.nav.emottak.melding.model.SendInRequest
import no.trygdeetaten.xml.eiff._1.EIFellesformat
import java.io.FileInputStream

object PasientlisteClient {
    fun hentPasientListe(request: SendInRequest): EIFellesformat {
        val url = "https://wasapp-q1.adeo.no/nav-emottak-practitioner-web/remoting/httpreqhandler-practitioner"
        val username = String(FileInputStream("/secret/serviceuser/username").readAllBytes())
        val password = String(FileInputStream("/secret/serviceuser/password").readAllBytes())
        val fellesformat = wrapMessageInEIFellesFormat(request)
        val marshalledFellesformat = FellesFormatXmlMarshaller.marshal(fellesformat)
        val httpClient = HttpClient(CIO)

        val result = runBlocking {
            try {
                httpClient.post(url) {
                    setBody(marshalledFellesformat)
                    contentType(ContentType.Application.Xml)
                    basicAuth(username, password)
                }.bodyAsText()
            } catch (e: Exception) {
                log.error("PasientlisteForesporsel error", e)
                throw e
            } finally {
                httpClient.close()
            }
        }

        log.info("HentPasientListe result: {}", result)

        // Not sure if this makes sense yet
        val unmarshalledResult = FellesFormatXmlMarshaller.unmarshal(result, EIFellesformat::class.java)

        log.info("HentPasientListe unmarshalled result: {}", unmarshalledResult)

        return unmarshalledResult
    }
}
