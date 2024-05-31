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

class CpaSyncService(private val cpaRepoClient: HttpClient) {

    private val log: Logger = LoggerFactory.getLogger("no.nav.emottak.smtp.cpasync")

    suspend fun sync(nfsConnector: NFSConnector = NFSConnector()) {
        return runCatching {
            val mutableCpaTimestamps = cpaRepoClient.getCPATimestamps().toMutableMap()
            processAndSyncEntries(mutableCpaTimestamps, nfsConnector)
            deleteStaleCpaEntries(mutableCpaTimestamps)
        }.onFailure {
            logFailure(it)
        }.getOrThrow()
    }

    private suspend fun processAndSyncEntries(
        mutableCpaTimestamps: MutableMap<String, String>,
        nfsConnector: NFSConnector
    ) {
        nfsConnector.use { connector ->
            connector.folder().forEach { entry ->
                val filename = entry.filename
                log.info("Checking $filename...")
                if (!filename.endsWith(".xml")) {
                    log.warn("$filename is ignored")
                    return@forEach
                }

                val lastModified = Date(entry.attrs.mTime.toLong() * 1000).toInstant()
                if (shouldSkipFile(filename, lastModified, mutableCpaTimestamps)) {
                    return@forEach
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
            }
        }
    }

    internal fun shouldSkipFile(
        filename: String,
        lastModified: Instant,
        mutableCpaTimestamps: MutableMap<String, String>
    ): Boolean {
        val shouldNotBeDeleted = ArrayList<String>()
        val matches = mutableCpaTimestamps.filter { (cpaId, timestamp) ->
            val formattedCpaId = cpaId.replace(":", ".")
            if (filename.contains(formattedCpaId)) {
                shouldNotBeDeleted.add(cpaId)
                if (Instant.parse(timestamp) == lastModified) {
                    log.info("$filename already exists with same timestamp")
                    true
                } else {
                    log.info("$filename has different timestamp, should be updated")
                    false
                }
            } else {
                false
            }
        }

        shouldNotBeDeleted.forEach { mutableCpaTimestamps.remove(it) }

        if (matches.any()) {
            log.info("Newer version already exists $filename, skipping...")
            return true
        }
        return false
    }

    internal suspend fun deleteStaleCpaEntries(cpaTimestamps: MutableMap<String, String>) {
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
