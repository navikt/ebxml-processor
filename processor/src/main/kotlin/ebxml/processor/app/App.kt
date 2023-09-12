/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package ebxml.processor.app

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.emottak.ebms.model.EbMSAttachment
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.processing.EbmsMessageProcessor
import no.nav.emottak.xml.EbmsMessageBuilder
import kotlin.io.encoding.ExperimentalEncodingApi

fun main() {

    val processor = EbmsMessageProcessor()

    embeddedServer(Netty, port = 8080) {
        routing {
            get("/") {
                call.respondText("Hello, world!")
            }
            post("/ebms") {
                val allParts = call.receiveMultipart().readAllParts()
                val dokument = allParts.find {
                    it.contentType?.toString() == "text/xml" && it.contentDisposition == null
                }
                val attachments = allParts.filter { it.contentDisposition == ContentDisposition.Attachment}
                val dokumentWithAttachment = EbMSDocument("",(dokument as PartData.FormItem).payload(),attachments.map { EbMSAttachment( (it as PartData.FormItem).payload(), it.contentType!!.contentType ,"contentId") })
                processor.process(dokumentWithAttachment)
                println(dokumentWithAttachment)

                call.respondText("Hello")
            }
        }
    }.start(wait = true)
}

fun PartData.FormItem.payload() : ByteArray {
    return java.util.Base64.getMimeDecoder().decode(this.value)
}
