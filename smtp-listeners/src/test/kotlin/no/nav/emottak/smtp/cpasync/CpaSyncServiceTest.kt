package no.nav.emottak.smtp.cpasync

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.emottak.deleteCPAinCPARepo
import no.nav.emottak.getCPATimestamps
import no.nav.emottak.nfs.NFSConnector
import no.nav.emottak.putCPAinCPARepo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CpaSyncServiceTest {

    private val cpaRepoClient: HttpClient = mockk(relaxed = true)
    private val mockHttpResponse: HttpResponse = mockk(relaxed = true)
    private lateinit var cpaSyncService: CpaSyncService

    @BeforeEach
    fun setUp() {
        mockkStatic("no.nav.emottak.HttpClientsKt")
        clearMocks(cpaRepoClient)
        coEvery { cpaRepoClient.deleteCPAinCPARepo(any()) } returns mockHttpResponse
        coEvery { cpaRepoClient.putCPAinCPARepo(any(), any()) } returns mockHttpResponse
    }

    @Test
    fun `sync should do nothing when all entries match own database`() = runBlocking {
        val cpaTimestampsFromDb = mutableMapOf("cpa1" to "2024-01-01T00:00:00Z", "cpa2" to "2024-01-01T00:00:00Z")
        val mockedAttrs = mockSftpAttrs(1704067200)
        val entries = listOf(
            mockLsEntry("cpa1.xml", mockedAttrs),
            mockLsEntry("cpa2.xml", mockedAttrs)
        )
        val mockedNFSConnector = mockNFSConnector(entries)
        cpaSyncService = CpaSyncService(cpaRepoClient, mockedNFSConnector)

        coEvery { cpaRepoClient.getCPATimestamps() } returns cpaTimestampsFromDb

        cpaSyncService.sync()

        coVerify(exactly = 0) { cpaRepoClient.putCPAinCPARepo(any(), any()) }
        coVerify(exactly = 0) { cpaRepoClient.deleteCPAinCPARepo(any()) }
    }

    @Test
    fun `sync should delete entry cpa2 from own database after it is gone from files`() = runBlocking {
        val cpaTimestampsFromDb = mutableMapOf("cpa1" to "2024-01-01T00:00:00Z", "cpa2" to "2024-01-01T00:00:00Z")
        val mockedAttrs = mockSftpAttrs(1704067200)
        val entries = listOf(mockLsEntry("cpa1.xml", mockedAttrs))
        val mockedNFSConnector = mockNFSConnector(entries)
        cpaSyncService = CpaSyncService(cpaRepoClient, mockedNFSConnector)

        coEvery { cpaRepoClient.getCPATimestamps() } returns cpaTimestampsFromDb

        cpaSyncService.sync()

        coVerify(exactly = 0) { cpaRepoClient.putCPAinCPARepo(any(), any()) }
        coVerify(exactly = 1) { cpaRepoClient.deleteCPAinCPARepo("cpa2") }
    }

    @Test
    fun `sync should insert new entry into our database after receiving it from files`() = runBlocking {
        val cpaTimestampsFromDb = mutableMapOf("cpa1" to "2024-01-01T00:00:00Z")
        val mockedAttrs = mockSftpAttrs(1704067200)
        val entries = listOf(
            mockLsEntry("cpa1.xml", mockedAttrs),
            mockLsEntry("cpa2.xml", mockedAttrs)
        )
        val mockedNFSConnector = mockNFSConnector(entries)
        cpaSyncService = CpaSyncService(cpaRepoClient, mockedNFSConnector)

        coEvery { cpaRepoClient.getCPATimestamps() } returns cpaTimestampsFromDb

        cpaSyncService.sync()

        coVerify(exactly = 1) {
            cpaRepoClient.putCPAinCPARepo(
                "simulated file content for /outbound/cpa/cpa2.xml",
                any()
            )
        }
        coVerify(exactly = 0) { cpaRepoClient.deleteCPAinCPARepo(any()) }
    }

    @Test
    fun `sync should update existing entry into our database after it have been modified in files`() = runBlocking {
        val lastModifiedDifferentThanFile = "2023-11-01T00:00:00Z"
        val cpaTimestampsFromDb =
            mutableMapOf("cpa1" to lastModifiedDifferentThanFile, "cpa2" to "2024-01-01T00:00:00Z")
        val mockedAttrs = mockSftpAttrs(1704067200)
        val entries = listOf(
            mockLsEntry("cpa1.xml", mockedAttrs),
            mockLsEntry("cpa2.xml", mockedAttrs)
        )
        val mockedNFSConnector = mockNFSConnector(entries)
        cpaSyncService = CpaSyncService(cpaRepoClient, mockedNFSConnector)
        coEvery { cpaRepoClient.getCPATimestamps() } returns cpaTimestampsFromDb

        cpaSyncService.sync()

        coVerify(exactly = 1) {
            cpaRepoClient.putCPAinCPARepo(
                "simulated file content for /outbound/cpa/cpa1.xml",
                any()
            )
        }
        coVerify(exactly = 0) { cpaRepoClient.deleteCPAinCPARepo(any()) }
    }

    @Test
    fun `sync should update an existing entry cpa1 and delete old entry cpa2 in database`() = runBlocking {
        val lastModifiedDifferentThanFile = "2023-11-01T00:00:00Z"
        val cpaTimestampsFromDb =
            mutableMapOf("cpa1" to lastModifiedDifferentThanFile, "cpa2" to "2024-01-01T00:00:00Z")
        val mockedAttrs = mockSftpAttrs(1704067200)
        val entries = listOf(
            mockLsEntry("cpa1.xml", mockedAttrs)
        )
        val mockedNFSConnector = mockNFSConnector(entries)
        cpaSyncService = CpaSyncService(cpaRepoClient, mockedNFSConnector)
        coEvery { cpaRepoClient.getCPATimestamps() } returns cpaTimestampsFromDb

        cpaSyncService.sync()

        coVerify(exactly = 1) {
            cpaRepoClient.putCPAinCPARepo(
                "simulated file content for /outbound/cpa/cpa1.xml",
                any()
            )
        }
        coVerify(exactly = 1) { cpaRepoClient.deleteCPAinCPARepo("cpa2") }
    }

    @Test
    fun `sync should insert new entry into our database only once`() = runBlocking {
        val firstRunCpaTimestampsFromDb = mutableMapOf("cpa1" to "2024-01-01T00:00:00Z")
        val secondRunCpaTimestampsFromDb =
            mutableMapOf("cpa1" to "2024-01-01T00:00:00Z", "cpa2" to "2024-01-01T00:00:00Z")
        var callCount = 0

        coEvery { cpaRepoClient.getCPATimestamps() } answers {
            callCount++
            when (callCount) {
                1 -> firstRunCpaTimestampsFromDb
                else -> secondRunCpaTimestampsFromDb
            }
        }

        val mockedAttrs = mockSftpAttrs(1704067200)
        val entries = listOf(
            mockLsEntry("cpa1.xml", mockedAttrs),
            mockLsEntry("cpa2.xml", mockedAttrs)
        )
        val mockedNFSConnector = mockNFSConnector(entries)
        cpaSyncService = CpaSyncService(cpaRepoClient, mockedNFSConnector)

        cpaSyncService.sync()
        cpaSyncService.sync()
        cpaSyncService.sync()
        cpaSyncService.sync()

        coVerify(exactly = 1) {
            cpaRepoClient.putCPAinCPARepo(
                "simulated file content for /outbound/cpa/cpa2.xml",
                any()
            )
        }
        coVerify(exactly = 0) {
            cpaRepoClient.putCPAinCPARepo(
                eq("simulated file content for /outbound/cpa/cpa1.xml"),
                any()
            )
        }
        coVerify(exactly = 1) {
            cpaRepoClient.putCPAinCPARepo(
                eq("simulated file content for /outbound/cpa/cpa2.xml"),
                any()
            )
        }
    }

    @Test
    fun `shouldSkipFile should return false (not skip) if the filename is not contained in mutable map`() {
        val filename = "cpa1.xml"
        val lastModified = Instant.parse("2024-01-01T00:00:00Z")
        val cpaTimestamps = mutableMapOf("cpa2" to "2023-12-31T23:59:59Z", "cpa3" to "2023-11-30T23:59:59Z")
        val mockedNFSConnector = mockNFSConnector(emptyList())
        cpaSyncService = spyk(CpaSyncService(cpaRepoClient, mockedNFSConnector))

        val result = cpaSyncService.shouldSkipFile(filename, lastModified, cpaTimestamps)

        assertFalse(result)
        assertTrue(cpaTimestamps.isNotEmpty())
    }

    @Test
    fun `shouldSkipFile should return true (skip) if the filename is contained in mutable map`() {
        val filename = "cpa1.xml"
        val lastModified = Instant.parse("2024-01-01T00:00:00Z")
        val cpaTimestamps = mutableMapOf("cpa1" to "2024-01-01T00:00:00Z", "cpa2" to "2023-12-31T23:59:59Z")
        val mockedNFSConnector = mockNFSConnector(emptyList())
        cpaSyncService = spyk(CpaSyncService(cpaRepoClient, mockedNFSConnector))

        val result = cpaSyncService.shouldSkipFile(filename, lastModified, cpaTimestamps)

        assertTrue(result)
        assertTrue(cpaTimestamps.isNotEmpty())
    }

    @Test
    fun `deleteStaleCpaEntries should delete remaining timestamps`() = runBlocking {
        val cpaTimestamps = mutableMapOf("cpa1" to "2024-01-01T00:00:00Z")
        val mockedNFSConnector = mockNFSConnector(emptyList())
        cpaSyncService = spyk(CpaSyncService(cpaRepoClient, mockedNFSConnector))

        cpaSyncService.deleteStaleCpaEntries(cpaTimestamps)

        coVerify { cpaRepoClient.deleteCPAinCPARepo("cpa1") }
    }

    @Test
    fun `sync should handle SftpException`() = runBlocking {
        val expectedSftpException = SftpException(4, "SFTP error")
        coEvery { cpaRepoClient.getCPATimestamps() } throws expectedSftpException
        val mockedNFSConnector = mockNFSConnector(emptyList())
        cpaSyncService = spyk(CpaSyncService(cpaRepoClient, mockedNFSConnector))

        val resultException = assertFailsWith<SftpException> {
            cpaSyncService.sync()
        }

        assert(expectedSftpException == resultException)
        verify { cpaSyncService.logFailure(expectedSftpException) }
    }

    @Test
    fun `sync should handle a generic exception`() = runBlocking {
        val expectedException = Exception("generic error")
        coEvery { cpaRepoClient.getCPATimestamps() } throws expectedException

        val mockedNFSConnector = mockNFSConnector(emptyList())
        cpaSyncService = spyk(CpaSyncService(cpaRepoClient, mockedNFSConnector))

        val resultException = assertFailsWith<Exception> {
            cpaSyncService.sync()
        }

        assert(expectedException == resultException)
        verify { cpaSyncService.logFailure(expectedException) }
    }

    private fun mockSftpAttrs(mTime: Int): SftpATTRS = mockk {
        every { this@mockk.mTime } returns mTime
    }

    private fun mockLsEntry(filename: String, attrs: SftpATTRS): ChannelSftp.LsEntry = mockk {
        every { this@mockk.filename } returns filename
        every { this@mockk.attrs } returns attrs
    }

    private fun mockNFSConnector(entries: List<ChannelSftp.LsEntry>): NFSConnector = mockk {
        every { folder() } returns Vector<ChannelSftp.LsEntry>().apply { addAll(entries) }
        every { file(any()) } answers { ByteArrayInputStream("simulated file content for ${it.invocation.args[0]}".toByteArray()) }
        every { close() } just Runs
    }
}
