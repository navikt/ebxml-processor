package no.nav.emottak.smtp

import com.jcraft.jsch.SftpException
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO_PARALLELISM_PROPERTY_NAME
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import net.logstash.logback.marker.Markers
import no.nav.emottak.deleteCPAinCPARepo
import no.nav.emottak.getCPATimestamps
import no.nav.emottak.getCpaRepoAuthenticatedClient
import no.nav.emottak.nfs.NFSConnector
import no.nav.emottak.postEbmsMessageMultiPart
import no.nav.emottak.postEbmsMessageSinglePart
import no.nav.emottak.putCPAinCPARepo
import org.eclipse.angus.mail.imap.IMAPFolder
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.Date
import kotlin.time.toKotlinDuration

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::myApplicationModule).start(wait = true)
}

internal val log = LoggerFactory.getLogger("no.nav.emottak.smtp")

fun Application.myApplicationModule() {
    install(ContentNegotiation) {
        json()
    }
    routing {

        if (getEnvVar("NAIS_CLUSTER_NAME") != "prod-fss") {
            mailCheck()
            mailRead()
            logOutgoing()

        }

        cpaSync()

    }
}






