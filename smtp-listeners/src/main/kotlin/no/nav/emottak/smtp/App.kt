package no.nav.emottak.smtp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpException
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.logstash.logback.marker.Markers
import no.nav.emottak.deleteCPAinCPARepo
import no.nav.emottak.getCPATimestamps
import no.nav.emottak.getLatestCPATimestamp
import no.nav.emottak.nfs.DummyUserInfo
import no.nav.emottak.nfs.NFSConfig
import no.nav.emottak.nfs.NFSConnector
import no.nav.emottak.postEbmsMessageMultiPart
import no.nav.emottak.postEbmsMessageSinglePart
import no.nav.emottak.putCPAinCPARepo
import org.eclipse.angus.mail.imap.IMAPFolder
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.Vector
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
        get("/test") {
            call.respond("Hello World!")
        }

        get("/testsftp") {
            val log = LoggerFactory.getLogger("no.nav.emottak.smtp.sftp")
            val httpClient = HttpClient(CIO) {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    json()
                }
            }
            withContext(Dispatchers.IO) {
                val startTime = Instant.now()
                runCatching {
                    val cpaTimestamps = httpClient.getCPATimestamps().toMutableMap() // mappen tømmes ettersom entries behandles
                    val timestampLatest = httpClient.getLatestCPATimestamp()
                    NFSConnector().use { connector ->
                        connector.folder().filter {
                            val lastModified = Date(it.attrs.mTime.toLong() * 1000).toInstant()
                            lastModified.isAfter(timestampLatest) &&
                                it.filename.endsWith(".xml")
                        }.forEach { entry ->
                            val lastModified = Date(entry.attrs.mTime.toLong() * 1000).toInstant()
                            val filename = entry.filename
                            runCatching {
                                log.info("Fetching file $filename")
                                val cpaFile = connector.file(filename).use {
                                    String(it.readAllBytes())
                                }
                                log.info("Uploading $filename")
                                httpClient.putCPAinCPARepo(cpaFile, lastModified)
                            }.onFailure {
                                log.error("Error uploading $filename to cpa-repo: ${it.message}", it)
                            }
                        }
                    }
                    // Any remaining timestamps means they exist in DB, but not in disk and should be cleaned
                    cpaTimestamps.forEach { (cpaId) ->
                        httpClient.deleteCPAinCPARepo(cpaId)
                    }
                }.onFailure {
                    when (it) {
                        is SftpException -> log.error("SftpException ID: [${it.id}]", it)
                        else -> log.error(it.message, it)
                    }
                }.onSuccess {
                    log.info("CPA synchronization completed in ${Duration.between(startTime, Instant.now()).toKotlinDuration()}")
                }
            }
            call.respond(HttpStatusCode.OK, "Hello World!")
        }

        get("/testsftp2_0") {
            val log = LoggerFactory.getLogger("no.nav.emottak.smtp.sftp")
            withContext(Dispatchers.IO) {
                val privateKeyFile = "/var/run/secrets/privatekey"
                val publicKeyFile = "/var/run/secrets/publickey"
                log.info(String(FileInputStream(publicKeyFile).readAllBytes()))
                // var privKey = """"""
                // var pubkey = """"""

                val jsch = JSch()
                val knownHosts =
                    "b27drvl011.preprod.local,10.183.32.98 ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBPHicnwpAS9dsHTlMm2NSm9BSu0yvacXHNCjvcJpMH8MEbJWAZ1/2EhdWxkeXueMnIOKJhEwK02kZ7FFUbzzWms="
                jsch.setKnownHosts(ByteArrayInputStream(knownHosts.toByteArray()))
                val nfsConfig = NFSConfig()
                // val privateKey = nfsConfig.nfsKey.toByteArray()
                jsch.addIdentity(privateKeyFile, publicKeyFile, "cpatest".toByteArray())
                // jsch.addIdentity("srvEmottakCPA",privKey.toByteArray(),pubkey.toByteArray(),"cpatest".toByteArray())
                val session = jsch.getSession("srvEmottakCPA", "10.183.32.98", 22)
                session.userInfo = DummyUserInfo()
                session.connect()
                val sftpChannel = session.openChannel("sftp") as ChannelSftp
                sftpChannel.connect()
                sftpChannel.cd("/outbound/cpa")
                val folder: Vector<ChannelSftp.LsEntry> = sftpChannel.ls(".") as Vector<ChannelSftp.LsEntry>

                val URL_CPA_REPO_BASE = getEnvVar("URL_CPA_REPO", "http://cpa-repo.team-emottak.svc.nais.local")
                val URL_CPA_REPO_PUT = URL_CPA_REPO_BASE + "/cpa"

                try {
                    val client = HttpClient(CIO) {
                        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                            json()
                        }
                    }
                    val cpaTimestamps =
                        Json.decodeFromString<Map<String, String>>(
                            client.get("$URL_CPA_REPO_BASE/cpa/timestamps")
                                .bodyAsText()
                        ).toMutableMap() // mappen tømmes ettersom entries behandles

                    val timestampLatest =
                        Json.decodeFromString<Instant>(
                            client.get("$URL_CPA_REPO_BASE/cpa/timestamps/latest")
                                .bodyAsText()
                        )

                    folder.filter {
                        val lastModified = Date(it.attrs.mTime.toLong() * 1000).toInstant()
                        lastModified.isAfter(timestampLatest) &&
                            it.filename.endsWith(".xml")
                    }.forEach {
                        val lastModified = Date(it.attrs.mTime.toLong() * 1000).toInstant()
                        log.info("Fetching file ${it.filename}")
                        val getFile = sftpChannel.get(it.filename)
                        log.info("Uploading " + it.filename)
                        val cpaFile = String(getFile.readAllBytes())
                        log.info("Length ${cpaFile.length}")
                        getFile.close()
                        try {
                            client.post(URL_CPA_REPO_PUT) {
                                headers {
                                    header("updated_date", lastModified.toString())
                                    header("upsert", "true") // Upsert kan nok alltid brukes (?)
                                }
                                setBody(cpaFile)
                            }
                        } catch (e: Exception) {
                            log.error("Error uploading ${it.filename} to cpa-repo: ${e.message}", e)
                        }
                    }
                    // Any remaining timestamps means they exist in DB, but not in disk and should be cleaned
                    cpaTimestamps.forEach { (cpaId) ->
                        client.delete("$URL_CPA_REPO_BASE/cpa/delete/$cpaId")
                    }
                } catch (e: Exception) {
                    if (e is SftpException) {
                        log.error("SftpException ID: [${e.id}]")
                    }
                    log.error(e.message, e)
                }
                sftpChannel.disconnect()
                session.disconnect()

                log.info("test key is" + nfsConfig.nfsKey.length)
            }
            call.respond(HttpStatusCode.OK, "Hello World!")
        }

        get("/mail/read") {
            val httpClient = HttpClient(CIO)
            call.respond(HttpStatusCode.OK, "Meldingslesing startet ...")
            var messageCount = 0
            val timeStart = Instant.now()
            runCatching {
                MailReader(incomingStore, false).use {
                    messageCount = it.count()
                    log.info("read ${it.count()} from innbox")
                    val asyncJobList: ArrayList<Deferred<Any>> = ArrayList()
                    do {
                        val messages = it.readMail()
                        messages.map { message ->
                            asyncJobList.add(
                                async(Dispatchers.IO) {
                                    runCatching {
                                        // withContext(Dispatchers.IO) {
                                        if (message.parts.size == 1 && message.parts.first().headers.isEmpty()) {
                                            httpClient.postEbmsMessageSinglePart(message)
                                        } else {
                                            httpClient.postEbmsMessageMultiPart(message)
                                        }
                                    }.onFailure {
                                        log.error(it.message, it)
                                    }
                                }
                            )
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
            // logBccMessages()
        }

        get("/mail/log/outgoing") {
            logBccMessages()
            call.respond(HttpStatusCode.OK)
        }
    }
}

fun logBccMessages() {
    val inbox = bccStore.getFolder("INBOX") as IMAPFolder
    val testDataInbox = bccStore.getFolder("testdata") as IMAPFolder
    testDataInbox.open(Folder.READ_WRITE)
    if (testDataInbox.messageCount > getEnvVar("INBOX_LIMIT", "2000").toInt()) {
        testDataInbox.messages.map {
            it.setFlag(Flags.Flag.DELETED, true)
            it
        }.toTypedArray().also {
            testDataInbox.expunge(it)
        }
    }
    inbox.open(Folder.READ_WRITE)
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
    }.also {
        inbox.moveMessages(inbox.messages, testDataInbox)
    }
    inbox.close()
    testDataInbox.close()
}
