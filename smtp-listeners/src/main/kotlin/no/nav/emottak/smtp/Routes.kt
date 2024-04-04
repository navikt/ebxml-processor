package no.nav.emottak.smtp

import com.jcraft.jsch.SftpException
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.internet.MimeMultipart
import java.time.Duration
import java.time.Instant
import java.util.Date
import kotlin.time.toKotlinDuration
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


fun Route.cpaSync() : Route = get("/cpa-sync") {
            val log = LoggerFactory.getLogger("no.nav.emottak.smtp.sftp")

            val cpaRepoClient = getCpaRepoAuthenticatedClient()
            withContext(Dispatchers.IO) {
                val startTime = Instant.now()
                runCatching {
                    val cpaTimestamps =
                        cpaRepoClient.getCPATimestamps().toMutableMap() // mappen tømmes ettersom entries behandles
                    NFSConnector().use { connector ->
                        connector.folder().forEach { entry ->
                            val filename = entry.filename
                            log.info("Checking $filename...")
                            if (!filename.endsWith(".xml")) {
                                log.warn(entry.filename + " is ignored")
                                return@forEach
                            }
                            val lastModified = Date(entry.attrs.mTime.toLong() * 1000).toInstant()
                            // Fjerner cpaId matches fra timestamp listen og skipper hvis nyere eksisterer
                            // Todo refactor. Too "kotlinesque":
                            with(ArrayList<String>()) {
                                cpaTimestamps.filter { cpaTimestamp ->
                                    cpaTimestamp.key
                                        .let { cpaId ->
                                            return@let if (filename.contains(cpaId.replace(":", "."))) {
                                                log.info("$filename already exists")
                                                add(cpaId)
                                                true
                                            } else {
                                                false
                                            }
                                        } && Instant.parse(cpaTimestamp.value)
                                        .isAfter(lastModified.minusSeconds(2)) // Litt løs sjekk siden ikke alle systemer har samme millisec presisjon
                                }.let { matches ->
                                    forEach { cpaTimestamps.remove(it) }
                                    if (matches.any()) {
                                        log.info("Newer version already exists $filename, skipping...")
                                        return@forEach
                                    }
                                }
                            }
                            runCatching {
                                log.info("Fetching file ${entry.filename}")
                                val cpaFile = connector.file("/outbound/cpa/" + entry.filename).use {
                                    String(it.readAllBytes())
                                }
                                log.info("Uploading $filename")
                                cpaRepoClient.putCPAinCPARepo(cpaFile, lastModified)
                            }.onFailure {
                                log.error("Error uploading $filename to cpa-repo: ${it.message}", it)
                            }
                        }
                    }
                    // Any remaining timestamps means they exist in DB, but not in disk and should be cleaned
                    cpaTimestamps.forEach { (cpaId) ->
                        cpaRepoClient.deleteCPAinCPARepo(cpaId)
                    }
                }.onFailure {
                    when (it) {
                        is SftpException -> log.error("SftpException ID: [${it.id}]", it)
                        else -> log.error(it.message, it)
                    }
                }.onSuccess {
                    log.info(
                        "CPA synchronization completed in ${
                            Duration.between(startTime, Instant.now()).toKotlinDuration()
                        }"
                    )
                    call.respond(HttpStatusCode.OK, "CPA sync complete")
                }
            }
            call.respond(HttpStatusCode.OK, "Hello World!")
        }


fun Route.mailCheck() : Route  = get("/mail/check") {
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

fun Route.mailRead() : Route = get("/mail/read") {
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

fun Route.logOutgoing() : Route =  get("/mail/log/outgoing") {
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
//}


fun logBccMessages() {
    if ("dev-fss" != getEnvVar("CLUSTER", "dev-fss")) {
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
        val deleteMeFolder = getFolder("DeleteMe")
        this.renameTo(deleteMeFolder)
        deleteMeFolder.delete(true)
        log.info("${this.fullName} deleted.")
        return
    }
    log.warn("DeleteAll strategy only valid for IMAP")
}



