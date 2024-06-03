package no.nav.emottak.smtp.cpasync

import com.jcraft.jsch.SftpException
import io.ktor.client.HttpClient
import no.nav.emottak.deleteCPAinCPARepo
import no.nav.emottak.getCPATimestamps
import no.nav.emottak.nfs.NFSConnector
import no.nav.emottak.putCPAinCPARepo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

class CpaSyncService(private val cpaRepoClient: HttpClient, private val nfsConnector: NFSConnector) {

    private val log: Logger = LoggerFactory.getLogger("no.nav.emottak.smtp.cpasync")

    suspend fun sync() {
        return runCatching {
            val cpaTimestamps = cpaRepoClient.getCPATimestamps()
            processAndSyncEntries(cpaTimestamps)
        }.onFailure {
            logFailure(it)
        }.getOrThrow()
    }

    private suspend fun processAndSyncEntries(cpaTimestamps: Map<String, String>) {
        nfsConnector.use { connector ->
            val staleCpaTimestamps = connector.folder().fold(cpaTimestamps.toMap()) { acc, entry ->
                val filename = entry.filename
                log.info("Checking $filename...")

                if (!filename.endsWith(".xml")) {
                    log.warn("$filename is ignored")
                    return@fold acc
                }

                val lastModified = Date(entry.attrs.mTime.toLong() * 1000).toInstant()
                val (shouldSkip, staleCpaTimestamps) = shouldSkipFile(filename, lastModified, acc)
                if (shouldSkip) {
                    return@fold staleCpaTimestamps
                }

                runCatching {
                    log.info("Fetching file ${entry.filename}")
                    val cpaFileContent = connector.file("/outbound/cpa/${entry.filename}").use {
                        String(it.readAllBytes())
                    }
                    log.info("Uploading $filename")
                    cpaRepoClient.putCPAinCPARepo(cpaFileContent, lastModified)
                }.onFailure {
                    log.error("Error uploading $filename to cpa-repo: ${it.message}", it)
                }

                staleCpaTimestamps
            }

            deleteStaleCpaEntries(staleCpaTimestamps)
        }
    }

    internal fun shouldSkipFile(
        filename: String,
        lastModified: Instant,
        cpaTimestamps: Map<String, String>
    ): Pair<Boolean, Map<String, String>> {
        var shouldSkip = false
        val staleCpaTimestamps = cpaTimestamps.filter { (cpaId, timestamp) ->
            val formattedCpaId = cpaId.replace(":", ".")
            if (filename.contains(formattedCpaId)) {
                if (timestamp == lastModified.toString()) {
                    log.info("$filename already exists with same timestamp")
                    shouldSkip = true
                } else {
                    log.info("$filename has different timestamp, should be updated")
                }
                false
            } else {
                true
            }
        }

        if (shouldSkip) {
            log.info("Newer version already exists $filename, skipping...")
        }

        return shouldSkip to staleCpaTimestamps
    }

    internal suspend fun deleteStaleCpaEntries(cpaTimestamps: Map<String, String>) {
        cpaTimestamps.forEach { (cpaId) ->
            cpaRepoClient.deleteCPAinCPARepo(cpaId)
        }
    }

    internal fun logFailure(throwable: Throwable) {
        when (throwable) {
            is SftpException -> log.error("SftpException ID: [${throwable.id}]", throwable)
            else -> log.error(throwable.message, throwable)
        }
    }
}
