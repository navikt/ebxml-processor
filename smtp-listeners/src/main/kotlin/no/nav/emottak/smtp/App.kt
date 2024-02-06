package no.nav.emottak.smtp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelSftp.LsEntry
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.UserInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
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
import io.ktor.util.CaseInsensitiveMap
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import net.logstash.logback.marker.Markers
import no.nav.emottak.nfs.NFSConfig
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

class DummyUserInfo : UserInfo {
    override fun getPassword(): String? {
        return passwd
    }

    override fun promptYesNo(str: String): Boolean {
        return true
    }

    var passwd: String? = null
    override fun getPassphrase(): String? {
        return null
    }

    override fun promptPassphrase(message: String): Boolean {
        return true
    }

    override fun promptPassword(message: String): Boolean {
        return true
    }

    override fun showMessage(message: String) {}
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
                val folder: Vector<LsEntry> = sftpChannel.ls(".") as Vector<LsEntry>

                val URL_CPA_REPO_BASE = getEnvVar("URL_CPA_REPO", "https://cpa-repo.intern.dev.nav.no")
                val URL_CPA_REPO_PUT = URL_CPA_REPO_BASE + "/cpa"

                try {
                    val client = HttpClient(CIO) {
                        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                            json()
                        }
                    }
                    val cpaTimestamps = client.get("$URL_CPA_REPO_BASE/cpa/timestamps")
                        .body<Map<String, String>>().toMutableMap() // mappen tømmes ettersom entries behandles

                    folder.forEach {
                        log.info("Checking ${it.filename}...")
                        if (!it.filename.endsWith(".xml")) {
                            log.warn(it.filename + " is ignored")
                            return@forEach
                        }
                        val lastModified = Date(it.attrs.mTime.toLong() * 1000).toInstant()
                        // Fjerner cpaId matches fra timestamp listen og skipper hvis nyere eksisterer
                        // Todo refactor. Too "kotlinesque":
                        with(ArrayList<String>()) {
                            cpaTimestamps.filter { cpaTimestamp ->
                                cpaTimestamp.key
                                    .let { cpaId ->
                                        if (it.filename.contains(cpaId.replace(":", "."))) {
                                            log.info("${it.filename} already exists")
                                            add(cpaId)
                                            return@let true
                                        }
                                        false
                                    } && Instant.parse(cpaTimestamp.value)
                                    .isAfter(lastModified.minusSeconds(2)) // Litt løs sjekk siden ikke alle systemer har samme millisec presisjon
                            }.let { matches ->
                                forEach { cpaTimestamps.remove(it) }
                                if (matches.any()) {
                                    log.info("Newer version already exists ${it.filename}, skipping...")
                                    return@forEach
                                }
                            }
                        }
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
            val client = HttpClient(CIO)
            call.respond(HttpStatusCode.OK, "Meldingslesing startet ...")
            var messageCount = 0
            var timeStart = Instant.now()
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
                                            client.post("https://ebms-provider.intern.dev.nav.no/ebms") {
                                                headers(
                                                    message.headers.filterHeader(
                                                        MimeHeaders.MIME_VERSION,
                                                        MimeHeaders.CONTENT_ID,
                                                        MimeHeaders.SOAP_ACTION,
                                                        MimeHeaders.CONTENT_TYPE,
                                                        MimeHeaders.CONTENT_TRANSFER_ENCODING,
                                                        SMTPHeaders.FROM,
                                                        SMTPHeaders.TO,
                                                        SMTPHeaders.MESSAGE_ID,
                                                        SMTPHeaders.DATE,
                                                        SMTPHeaders.X_MAILER
                                                    )
                                                )
                                                setBody(
                                                    message.parts.first().bytes
                                                )
                                            }
                                        } else {
                                            val partData: List<PartData> = message.parts.map { part ->
                                                PartData.FormItem(
                                                    String(part.bytes),
                                                    {},
                                                    Headers.build(
                                                        part.headers.filterHeader(
                                                            MimeHeaders.CONTENT_ID,
                                                            MimeHeaders.CONTENT_TYPE,
                                                            MimeHeaders.CONTENT_TRANSFER_ENCODING,
                                                            MimeHeaders.CONTENT_DISPOSITION,
                                                            MimeHeaders.CONTENT_DESCRIPTION
                                                        )
                                                    )
                                                )
                                            }
                                            val contentType = message.headers[MimeHeaders.CONTENT_TYPE]!!
                                            val boundary = ContentType.parse(contentType).parameter("boundary")

                                            client.post("https://ebms-provider.intern.dev.nav.no/ebms") {
                                                headers(
                                                    message.headers.filterHeader(
                                                        MimeHeaders.MIME_VERSION,
                                                        MimeHeaders.CONTENT_ID,
                                                        MimeHeaders.SOAP_ACTION,
                                                        MimeHeaders.CONTENT_TYPE,
                                                        MimeHeaders.CONTENT_TRANSFER_ENCODING,
                                                        SMTPHeaders.FROM,
                                                        SMTPHeaders.TO,
                                                        SMTPHeaders.MESSAGE_ID,
                                                        SMTPHeaders.DATE,
                                                        SMTPHeaders.X_MAILER
                                                    )
                                                )
                                                setBody(
                                                    MultiPartFormDataContent(
                                                        partData,
                                                        boundary!!,
                                                        ContentType.parse(contentType)
                                                    )
                                                )
                                            }
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

fun Map<String, String>.filterHeader(vararg headerNames: String): HeadersBuilder.() -> Unit = {
    val caseInsensitiveMap = CaseInsensitiveMap<String>().apply {
        putAll(this@filterHeader)
    }
    headerNames.map {
        Pair(it, caseInsensitiveMap[it])
    }.forEach {
        if (it.second != null) {
            val headerValue = MimeUtility.unfold(it.second!!.replace("\t", " "))
            append(it.first, headerValue)
        }
    }

    appendMessageIdAsContentIdIfContentIdIsMissingOnTextXMLContentTypes(caseInsensitiveMap)
    if (headerNames.contains(MimeHeaders.CONTENT_DISPOSITION) && headerNames.contains(MimeHeaders.CONTENT_DESCRIPTION)) {
        appendContentDescriptionAsContentDispositionIfDispositionIsMissing(caseInsensitiveMap)
    }
}

private fun HeadersBuilder.appendMessageIdAsContentIdIfContentIdIsMissingOnTextXMLContentTypes(
    caseInsensitiveMap: CaseInsensitiveMap<String>
) {
    if (MimeUtility.unfold(caseInsensitiveMap[MimeHeaders.CONTENT_TYPE])?.contains("text/xml") == true) {
        if (caseInsensitiveMap[MimeHeaders.CONTENT_ID] != null) {
            log.warn(
                "Content-Id header allerede satt for text/xml: " + caseInsensitiveMap[MimeHeaders.CONTENT_ID] +
                    "\nMessage-Id: " + caseInsensitiveMap[SMTPHeaders.MESSAGE_ID]
            )
        } else {
            val headerValue = MimeUtility.unfold(caseInsensitiveMap[SMTPHeaders.MESSAGE_ID]!!.replace("\t", " "))
            append(MimeHeaders.CONTENT_ID, headerValue)
            log.info("Header: <${MimeHeaders.CONTENT_ID}> - <$headerValue>")
        }
    }
}

private fun HeadersBuilder.appendContentDescriptionAsContentDispositionIfDispositionIsMissing(
    caseInsensitiveMap: CaseInsensitiveMap<String>
) {
    if (caseInsensitiveMap[MimeHeaders.CONTENT_DESCRIPTION] != null && caseInsensitiveMap[MimeHeaders.CONTENT_DISPOSITION] == null) {
        val headerValue = MimeUtility.unfold(caseInsensitiveMap[MimeHeaders.CONTENT_DESCRIPTION]!!.replace("\t", " "))
        append(MimeHeaders.CONTENT_DISPOSITION, headerValue)
    }
}
