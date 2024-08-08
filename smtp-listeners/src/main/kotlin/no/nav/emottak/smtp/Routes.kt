package no.nav.emottak.smtp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Folder.HOLDS_MESSAGES
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO_PARALLELISM_PROPERTY_NAME
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import net.logstash.logback.marker.Markers
import no.nav.emottak.URL_CPA_REPO_BASE
import no.nav.emottak.getCpaRepoAuthenticatedClient
import no.nav.emottak.nfs.NFSConnector
import no.nav.emottak.postEbmsMessageMultiPart
import no.nav.emottak.postEbmsMessageSinglePart
import no.nav.emottak.smtp.cpasync.CpaSyncService
import no.nav.emottak.util.measureTimeSuspended
import org.eclipse.angus.mail.imap.IMAPFolder
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration

fun Route.cpaSync(): Route = get("/cpa-sync") {
    val log = LoggerFactory.getLogger("no.nav.emottak.smtp.sftp")
    val cpaSyncService = CpaSyncService(getCpaRepoAuthenticatedClient(), NFSConnector())

    withContext(Dispatchers.IO) {
        log.info("Starting CPA sync")
        val (result, duration) = measureTimeSuspended {
            runCatching {
                cpaSyncService.sync()
            }
        }
        result.onSuccess {
            log.info("CPA sync completed in $duration")
            call.respond(HttpStatusCode.OK, "CPA sync complete")
        }.onFailure {
            call.respond(HttpStatusCode.InternalServerError, "Something went wrong")
        }
    }

    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
}

fun Route.mailCheck(): Route = get("/mail/check") {
    val report = mutableMapOf<String, String>()
    incomingStore.getFolder("INBOX").use {
        it.open(Folder.READ_ONLY)
        report["incomingStore Inbox"] = it.messageCount.toString()
    }

    bccStore.getFolder("INBOX").use {
        it.open(Folder.READ_ONLY)
        report["bccStore Inbox"] = it.messageCount.toString()
    }

    bccStore.getFolder("testdata").use {
        it.open(Folder.READ_ONLY)
        report["bccStore testdata"] = it.messageCount.toString()
    }
    call.respond(HttpStatusCode.OK, report)
}

fun Route.mailRead(): Route = get("/mail/read") {
    val httpClient = HttpClient(CIO)
    call.respond(HttpStatusCode.OK, "Meldingslesing startet ...")
    var messageCount = 0
    val timeStart = Instant.now()
    val dryRun = getEnvVar("DRY_RUN", "false")
    runCatching {
        MailReader(incomingStore, false).use {
            messageCount = it.count()
            log.info("read ${it.count()} from innbox")
            val asyncJobList: ArrayList<Deferred<Any>> = ArrayList()
            var mailCounter = 0
            do {
                val messages = it.readMail()
                messages.map { message ->
                    asyncJobList.add(
                        async(Dispatchers.IO) {
                            runCatching {
                                // withContext(Dispatchers.IO) {
                                if (dryRun != "true") {
                                    if (message.parts.size == 1 && message.parts.first().headers.isEmpty()) {
                                        httpClient.postEbmsMessageSinglePart(message)
                                    } else {
                                        httpClient.postEbmsMessageMultiPart(message)
                                    }
                                }
                            }.onFailure {
                                log.error(it.message, it)
                            }
                        }
                    )
                }
                mailCounter += 1
                if (mailCounter < (System.getProperty(IO_PARALLELISM_PROPERTY_NAME) ?: "64").toInt()) {
                    asyncJobList.awaitAll()
                    asyncJobList.clear()
                    mailCounter = 0
                }
                log.info("Inbox has messages ${messages.isNotEmpty()}")
            } while (messages.isNotEmpty())
            asyncJobList.awaitAll()
        }
    }.onSuccess {
        val timeToCompletion = Duration.between(timeStart, Instant.now())
        val throughputPerMinute = messageCount / (timeToCompletion.toMillis().toDouble() / 1000 / 60)
        log.info(
            Markers.appendEntries(mapOf(Pair("MailReaderTPM", throughputPerMinute))),
            "$messageCount processed in ${timeToCompletion.toKotlinDuration()},($throughputPerMinute tpm)"
        )
        call.respond(HttpStatusCode.OK, "Meldinger Lest")
    }.onFailure {
        log.error(it.message, it)
        call.respond(it.localizedMessage)
    }
    logBccMessages()
}

fun Route.testAzureAuthToCpaRepo(): Route = get("/testCpaRepoConnection") {
    val cpaRepoClient = getCpaRepoAuthenticatedClient()
    call.respond(
        cpaRepoClient.get("$URL_CPA_REPO_BASE/whoami").bodyAsText()
    )
}

fun Route.logOutgoing(): Route = get("/mail/log/outgoing") {
    logBccMessages()
    call.respond(HttpStatusCode.OK)
}

//        get("/mail/nuke") { // TODO fjern før prod
//            incomingStore.getFolder("INBOX")
//                .batchDelete(100)
//            bccStore.getFolder("INBOX")
//                .batchDelete(100)
//            bccStore.getFolder("testdata")
//                .batchDelete(100)
//            call.respond(HttpStatusCode.OK)
//        }
//   }
// }

fun logBccMessages() {
    if ("dev-fss" != getEnvVar("NAIS_CLUSTER_NAME", "dev-fss")) {
        return
    }
    val inbox = bccStore.getFolder("INBOX") as IMAPFolder
    val testDataInbox = bccStore.getFolder("testdata") as IMAPFolder
    testDataInbox.open(Folder.READ_WRITE)
    if (testDataInbox.messageCount > getEnvVar("INBOX_LIMIT", "2000").toInt()) {
//        testDataInbox.messages.map {  // TODO slett denne koden hvis deleteAll() funker
//            it.setFlag(Flags.Flag.DELETED, true)
//            it
//        }.toTypedArray().also {
//            testDataInbox.expunge(it)
//        }
        testDataInbox.deleteAll()
    }
    inbox.open(Folder.READ_WRITE)
    val expunge = inbox.messageCount > getEnvVar("INBOX_LIMIT", "2000").toInt()
    inbox.messages.forEach {
        if (it.content is MimeMultipart) {
            runCatching {
                (it.content as MimeMultipart).getBodyPart(0)
            }.onSuccess {
                log.info(
                    "Incoming multipart request with headers ${
                    it.allHeaders.toList().map { it.name + ":" + it.value }
                    }" +
                        "with body ${String(it.inputStream.readAllBytes())}"
                )
            }
        } else {
            log.info("Incoming singlepart request ${String(it.inputStream.readAllBytes())}")
        }
        it.setFlag(Flags.Flag.DELETED, expunge)
    }.also {
        inbox.moveMessages(inbox.messages, testDataInbox)
    }
    inbox.close()
    testDataInbox.close()
}

fun Folder.batchDelete(batchSize: Int) { // fixme: Skriv en test for denne før evt use
    val totalMessages = messageCount
    var previousMsgNum = 1
    do {
        this.open(Folder.READ_WRITE)
        val end = minOf(batchSize - 1, this.messageCount)
        log.info("Deleting in ${this.fullName} message $previousMsgNum to ${previousMsgNum + end} out of $totalMessages")
        this.getMessages(1, end).forEach { message ->
            message.setFlag(Flags.Flag.DELETED, true)
        }
        this.close(true)
        previousMsgNum += end
    } while (totalMessages > previousMsgNum)
}

fun Folder.deleteAll() {
    if (this is IMAPFolder) {
        if (isOpen) close()
        if (name.lowercase().contains("inbox")) {
            val deleteMeFolder = getFolder("DeleteMe")
            if (!deleteMeFolder.exists()) create(HOLDS_MESSAGES)
            this.renameTo(deleteMeFolder)
            deleteMeFolder.delete(true)
            log.info("${this.fullName} deleted.")
        } else {
            delete(true)
            log.info("${this.fullName} deleted.")
        }
    } else {
        log.warn("DeleteAll strategy only valid for IMAP")
    }
}

var BUG_ENCOUNTERED_CPA_REPO_TIMEOUT = false

fun Routing.registerHealthEndpoints() {
    get("/internal/health/liveness") {
        if (BUG_ENCOUNTERED_CPA_REPO_TIMEOUT) { // TODO : årsak ukjent, cpa-repo/timestamps endepunkt timer ut etter en stund
            call.respond(HttpStatusCode.ServiceUnavailable, "Restart me X_X")
        } else {
            call.respondText("I'm alive! :)")
        }
    }
    get("/internal/health/readiness") {
        call.respondText("I'm ready! :)")
    }
}
