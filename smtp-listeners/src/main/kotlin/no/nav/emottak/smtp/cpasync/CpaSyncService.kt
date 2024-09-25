package no.nav.emottak.smtp.cpasync

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import io.ktor.client.HttpClient
import no.nav.emottak.deleteCPAinCPARepo
import no.nav.emottak.getCPATimestamps
import no.nav.emottak.nfs.NFSConnector
import no.nav.emottak.putCPAinCPARepo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class NfsCpa(val id: String, val timestamp: String, val content: ByteArray)

class CpaSyncService(private val cpaRepoClient: HttpClient, private val nfsConnector: NFSConnector) {
    private val log: Logger = LoggerFactory.getLogger("no.nav.emottak.smtp.cpasync")

    suspend fun sync() {
        return runCatching {
            val dbCpaMap = cpaRepoClient.getCPATimestamps()
            val nfsCpaMap = getNfsCpaMap()
            upsertFreshCpa(nfsCpaMap, dbCpaMap)
            deleteStaleCpa(nfsCpaMap.keys, dbCpaMap)
        }.onFailure {
            logFailure(it)
        }.getOrThrow()
    }

    internal fun getNfsCpaMap(): Map<String, NfsCpa> {
        return nfsConnector.use { connector ->
            connector.folder().asSequence()
                .filter { entry -> isXmlFileEntry(entry) }
                .fold(mutableMapOf()) { accumulator, nfsCpaFile ->
                    val nfsCpa = getNfsCpa(connector, nfsCpaFile) ?: return accumulator

                    val existingEntry = accumulator.put(nfsCpa.id, nfsCpa)
                    require(existingEntry == null) { "NFS contains duplicate CPA IDs. Aborting sync." }

                    accumulator
                }
        }
    }

    internal fun isXmlFileEntry(entry: ChannelSftp.LsEntry): Boolean {
        if (entry.filename.endsWith(".xml")) {
            return true
        }
        log.debug("${entry.filename} is ignored. Invalid file ending")
        return false
    }

    internal fun getNfsCpa(connector: NFSConnector, nfsCpaFile: ChannelSftp.LsEntry): NfsCpa? {
        val timestamp = getLastModified(nfsCpaFile.attrs.mTime.toLong())
        val cpaContent = fetchNfsCpaContent(connector, nfsCpaFile)
        val cpaId = getCpaIdFromCpaContent(cpaContent)

        if (cpaId == null) {
            log.warn("Regex to find CPA ID in file ${nfsCpaFile.filename} did not find any match. File corrupted or wrongful regex.")
            return null
        }

        val zippedCpaContent = zipCpaContent(cpaContent)

        return NfsCpa(cpaId, timestamp, zippedCpaContent)
    }

    private fun fetchNfsCpaContent(nfsConnector: NFSConnector, nfsCpaFile: ChannelSftp.LsEntry): String {
        return nfsConnector.file("/outbound/cpa/${nfsCpaFile.filename}").use {
            String(it.readAllBytes())
        }
    }

    private fun getCpaIdFromCpaContent(cpaContent: String): String? {
        return Regex("cpaid=\"(?<cpaId>.+?)\"")
            .find(cpaContent)?.groups?.get("cpaId")?.value
    }

    internal fun getLastModified(mTimeInSeconds: Long): String {
        return Instant.ofEpochSecond(mTimeInSeconds).truncatedTo(ChronoUnit.SECONDS).toString()
    }

    internal fun zipCpaContent(cpaContent: String): ByteArray {
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream)
            .bufferedWriter(StandardCharsets.UTF_8)
            .use { it.write(cpaContent) }
        return byteStream.toByteArray()
    }

    private suspend fun upsertFreshCpa(nfsCpaMap: Map<String, NfsCpa>, dbCpaMap: Map<String, String>) {
        nfsCpaMap.forEach { entry ->
            if (shouldUpsertCpa(entry.value.timestamp, dbCpaMap[entry.key])) {
                log.info("Upserting new/modified CPA: ${entry.key} - ${entry.value.timestamp}")
                val unzippedCpaContent = unzipCpaContent(entry.value.content)
                cpaRepoClient.putCPAinCPARepo(unzippedCpaContent, entry.value.timestamp)
            } else {
                log.debug("Skipping upsert for unmodified CPA: ${entry.key} - ${entry.value.timestamp}")
            }
        }
    }

    internal fun unzipCpaContent(byteArray: ByteArray): String {
        return GZIPInputStream(byteArray.inputStream()).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    internal fun shouldUpsertCpa(nfsTimestamp: String, dbTimestamp: String?): Boolean {
        if (dbTimestamp == null) return true
        return Instant.parse(nfsTimestamp) > Instant.parse(dbTimestamp)
    }

    private suspend fun deleteStaleCpa(nfsCpaIds: Set<String>, dbCpaMap: Map<String, String>) {
        val staleCpa = dbCpaMap - nfsCpaIds
        staleCpa.forEach { entry ->
            log.info("Deleting stale entry: ${entry.key} - ${entry.value}")
            cpaRepoClient.deleteCPAinCPARepo(entry.key)
        }
    }

    internal fun logFailure(throwable: Throwable) {
        when (throwable) {
            is SftpException -> log.error("SftpException ID: [${throwable.id}]", throwable)
            else -> log.error(throwable.message, throwable)
        }
    }
}
