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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CpaSyncServiceTest {
    private val mockCpaRepoClient: HttpClient = mockk(relaxed = true)
    private val mockHttpResponse: HttpResponse = mockk(relaxed = true)
    private val mockNFSConnector: NFSConnector = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkStatic("no.nav.emottak.HttpClientsKt")
        clearMocks(mockCpaRepoClient)
        coEvery { mockCpaRepoClient.deleteCPAinCPARepo(any()) } returns mockHttpResponse
        coEvery { mockCpaRepoClient.putCPAinCPARepo(any(), any()) } returns mockHttpResponse
    }

    @Test
    fun `should return true for XML file`() {
        val lsEntry = mockLsEntry("nav.qass.12345.xml", "2024-01-01T00:00:00Z")
        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNFSConnector)

        assertTrue(cpaSyncService.isXmlFileEntry(lsEntry))
    }

    @Test
    fun `should return false for invalid file type`() {
        val lsEntry = mockLsEntry("nav.qass.12345.txt", "2024-01-01T00:00:00Z")
        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNFSConnector)

        assertFalse(cpaSyncService.isXmlFileEntry(lsEntry))
    }

    @Test
    fun `should not send request if invalid file type`() = runBlocking {
        val lsEntry = mockLsEntry("nav.qass.12345.txt", "2025-01-01T00:00:00Z")
        val mockNfs = mockNfsFromEntries(listOf(lsEntry), listOf("nav:qass:12345"))

        mockCpaRepoFromMap(emptyMap())

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)
        cpaSyncService.sync()

        coVerify(exactly = 0) { mockCpaRepoClient.putCPAinCPARepo(any(), any()) }
        coVerify(exactly = 0) { mockCpaRepoClient.deleteCPAinCPARepo(any()) }
    }

    @Test
    fun `should get cpaId from xml content`() = runBlocking {
        val lsEntry = mockLsEntry("nav.qass.12345.xml", "2025-01-01T00:00:00Z")
        val mockNfs = mockNfsFromEntries(listOf(lsEntry), listOf("nav:qass:12345"))

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)
        val nfsCpa = cpaSyncService.getNfsCpa(mockNfs, lsEntry)

        assertEquals("nav:qass:12345", nfsCpa?.id)
    }

    @Test
    fun `should find cpaId from an actual CPA file`() = runBlocking {
        val lsEntry = mockLsEntry("nav.qass.12345.txt", "2025-01-01T00:00:00Z")
        val mockNfs: NFSConnector = mockk {
            every { folder() } returns Vector<ChannelSftp.LsEntry>().apply { add(lsEntry) }
            every { file(any()) } returns File(ClassLoader.getSystemResource("cpa/nav.qass.12345.xml").file).inputStream()
            every { close() } just Runs
        }

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)
        val nfsCpa = cpaSyncService.getNfsCpa(mockNfs, lsEntry)

        assertEquals("nav:qass:12345", nfsCpa?.id)
    }

    @Test
    fun `should return null if cpa ID is not found`() = runBlocking {
        val lsEntry = mockLsEntry("nav.qass.missing.txt", "2025-01-01T00:00:00Z")
        val mockNfs = mockNfsFromEntries(listOf(lsEntry), listOf(""))

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)
        val cpa = cpaSyncService.getNfsCpa(mockNfs, lsEntry)

        assertTrue(cpa == null)
    }

    @Test
    fun `nfs mTime conversion to db timestamp should be accurate`() {
        val mTimeInSeconds = 1704067200L
        val expectedTimestamp = "2024-01-01T00:00:00Z"

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNFSConnector)

        val actualTimestamp = cpaSyncService.getLastModified(mTimeInSeconds)

        assert(expectedTimestamp == actualTimestamp)
    }

    @Test
    fun `zipping and unzipping should compress the file and return the same result`() {
        val cpaFile = File(ClassLoader.getSystemResource("cpa/nav.qass.12345.xml").file).readText()

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNFSConnector)

        val zipped = cpaSyncService.zipCpaContent(cpaFile)
        val unzipped = cpaSyncService.unzipCpaContent(zipped)

        assert(zipped.size < unzipped.toByteArray().size)
        assert(cpaFile == unzipped)
    }

    @Test
    fun `sync should abort if duplicate cpa IDs is found in nfs`() = runBlocking {
        val lsEntries = listOf(
            mockLsEntry("nav.qass.12345.xml", "2025-01-01T00:00:00Z"),
            mockLsEntry("nav.qass.12345.xml", "2025-01-01T00:00:00Z")
        )
        val mockNfs = mockNfsFromEntries(lsEntries, listOf("nav:qass:12345", "nav:qass:12345"))

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)

        val exception = assertThrows<IllegalArgumentException> {
            cpaSyncService.getNfsCpaMap()
        }

        assertTrue(exception.message!!.contains("NFS contains duplicate CPA IDs. Aborting sync."))
    }

    @Test
    fun `should skip processing if cpa ID is not found in CPA`() = runBlocking {
        val lsEntry = mockLsEntry("nav.qass.missing.txt", "2025-01-01T00:00:00Z")
        val mockNfs = mockNfsFromEntries(listOf(lsEntry), listOf(""))

        val dbCpa = emptyMap<String, String>()
        mockCpaRepoFromMap(dbCpa)

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)
        cpaSyncService.sync()

        coVerify(exactly = 0) { mockCpaRepoClient.putCPAinCPARepo(any(), any()) }
        coVerify(exactly = 0) { mockCpaRepoClient.deleteCPAinCPARepo(any()) }
    }

    @Test
    fun `sync should do nothing when all entries match database`() = runBlocking {
        val nfsCpa = mapOf("nav:qass:12345" to "2024-01-01T00:00:00Z")
        val dbCpa = mapOf("nav:qass:12345" to "2024-01-01T00:00:00Z")

        val mockNfs = mockNfsFromMap(nfsCpa)
        mockCpaRepoFromMap(dbCpa)

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)
        cpaSyncService.sync()

        coVerify(exactly = 0) { mockCpaRepoClient.putCPAinCPARepo(any(), any()) }
        coVerify(exactly = 0) { mockCpaRepoClient.deleteCPAinCPARepo(any()) }
    }

    @Test
    fun `sync should ignore entries when db timestamp is newer`() = runBlocking {
        val nfsCpa = mapOf("nav:qass:12345" to "2024-01-01T00:00:00Z")
        val dbCpa = mapOf("nav:qass:12345" to "2025-01-01T00:00:00Z")

        val mockNfs = mockNfsFromMap(nfsCpa)
        mockCpaRepoFromMap(dbCpa)

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)
        cpaSyncService.sync()

        coVerify(exactly = 0) { mockCpaRepoClient.putCPAinCPARepo(any(), any()) }
        coVerify(exactly = 0) { mockCpaRepoClient.deleteCPAinCPARepo(any()) }
    }

    @Test
    fun `sync should upsert when nfs timestamp is newer`() = runBlocking {
        val nfsCpa = mapOf("nav:qass:12345" to "2025-01-01T00:00:00Z")
        val dbCpa = mapOf("nav:qass:12345" to "2024-01-01T00:00:00Z")

        val mockNfs = mockNfsFromMap(nfsCpa)
        mockCpaRepoFromMap(dbCpa)

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)
        cpaSyncService.sync()

        coVerify(exactly = 1) { mockCpaRepoClient.putCPAinCPARepo(any(), any()) }
    }

    @Test
    fun `sync should upsert when entry does not exist in db`() = runBlocking {
        val nfsCpa = mapOf("nav:qass:12345" to "2024-01-01T00:00:00Z")
        val dbCpa = emptyMap<String, String>()

        val mockNfs = mockNfsFromMap(nfsCpa)
        mockCpaRepoFromMap(dbCpa)

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)
        cpaSyncService.sync()

        coVerify(exactly = 1) { mockCpaRepoClient.putCPAinCPARepo(any(), any()) }
    }

    @Test
    fun `sync should only delete entries that are stale`() = runBlocking {
        val nfsCpa = mapOf("nav:qass:12345" to "2024-01-01T00:00:00Z")
        val dbCpa = mapOf(
            "nav:qass:12345" to "2024-01-01T00:00:00Z",
            "nav:qass:67890" to "2024-01-01T00:00:00Z"
        )

        val mockNfs = mockNfsFromMap(nfsCpa)
        mockCpaRepoFromMap(dbCpa)

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)
        cpaSyncService.sync()

        coVerify(exactly = 1) { mockCpaRepoClient.deleteCPAinCPARepo("nav:qass:67890") }
    }

    @Test
    fun `sync should delete and upsert in same sync`() = runBlocking {
        val nfsCpa = mapOf("nav:qass:12345" to "2024-01-01T00:00:00Z")
        val dbCpa = mapOf("nav:qass:67890" to "2024-01-01T00:00:00Z")

        val mockNfs = mockNfsFromMap(nfsCpa)
        mockCpaRepoFromMap(dbCpa)

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)
        cpaSyncService.sync()

        coVerify(exactly = 1) { mockCpaRepoClient.putCPAinCPARepo(any(), any()) }
        coVerify(exactly = 1) { mockCpaRepoClient.deleteCPAinCPARepo("nav:qass:67890") }
    }

    @Test
    fun `sync should upsert correct CPA content`() = runBlocking {
        val nfsCpa = mapOf("nav:qass:12345" to "2024-01-01T00:00:00Z")
        val dbCpa = emptyMap<String, String>()

        val mockNfs = mockNfsFromMap(nfsCpa)
        mockCpaRepoFromMap(dbCpa)

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)
        cpaSyncService.sync()

        val expectedFileContent = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <cppa:CollaborationProtocolAgreement cppa:cpaid="nav:qass:12345">
                <!-- CPA content removed -->
            </cppa:CollaborationProtocolAgreement>
        """.trimIndent()

        coVerify(exactly = 1) {
            mockCpaRepoClient.putCPAinCPARepo(expectedFileContent, "2024-01-01T00:00:00Z")
        }
    }

    @Test
    fun `multiple syncs should persist CPA only once`() = runBlocking {
        val lsEntry = mockLsEntry("nav.qass.12345.xml", "2025-01-01T00:00:00Z")
        val mockNfs: NFSConnector = mockk {
            every { folder() } returns Vector<ChannelSftp.LsEntry>().apply { add(lsEntry) }
            every { file(any()) } answers { ByteArrayInputStream(simulateFileContent("nav:qass:12345").toByteArray()) }
            every { close() } just Runs
        }

        val dbCpaMapFirst = mapOf("nav:qass:12345" to "2024-01-01T00:00:00Z")
        val dbCpaMapSecond = mapOf("nav:qass:12345" to "2025-01-01T00:00:00Z")

        var callCount = 0
        coEvery { mockCpaRepoClient.getCPATimestamps() } answers {
            callCount++
            when (callCount) {
                1 -> dbCpaMapFirst
                else -> dbCpaMapSecond
            }
        }

        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNfs)

        cpaSyncService.sync()
        cpaSyncService.sync()
        cpaSyncService.sync()

        coVerify(exactly = 1) {
            mockCpaRepoClient.putCPAinCPARepo(any(), any())
        }
        coVerify(exactly = 0) {
            mockCpaRepoClient.deleteCPAinCPARepo(any())
        }
    }

    @Test
    fun `upsert check should return true if nfs timestamp is newer than db timestamp`() {
        val cpaSyncService = CpaSyncService(mockCpaRepoClient, mockNFSConnector)

        assertTrue { cpaSyncService.shouldUpsertCpa("2025-01-01T00:00:00Z", null) }
        assertTrue { cpaSyncService.shouldUpsertCpa("2025-01-01T00:00:00Z", "2024-01-01T00:00:00Z") }
        assertFalse { cpaSyncService.shouldUpsertCpa("2024-01-01T00:00:00Z", "2025-01-01T00:00:00Z") }
        assertFalse { cpaSyncService.shouldUpsertCpa("2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z") }
    }

    @Test
    fun `sync should handle SftpException`() = runBlocking {
        val expectedSftpException = SftpException(4, "SFTP error")
        coEvery { mockCpaRepoClient.getCPATimestamps() } throws expectedSftpException
        val mockedNFSConnector = mockNfsFromMap(emptyMap())
        val cpaSyncService = spyk(CpaSyncService(mockCpaRepoClient, mockedNFSConnector))

        val resultException = assertFailsWith<SftpException> {
            cpaSyncService.sync()
        }

        assert(expectedSftpException == resultException)
        verify { cpaSyncService.logFailure(expectedSftpException) }
    }

    @Test
    fun `sync should handle a generic exception`() = runBlocking {
        val expectedException = Exception("generic error")
        coEvery { mockCpaRepoClient.getCPATimestamps() } throws expectedException

        val mockedNFSConnector = mockNfsFromMap(emptyMap())
        val cpaSyncService = spyk(CpaSyncService(mockCpaRepoClient, mockedNFSConnector))

        val resultException = assertFailsWith<Exception> {
            cpaSyncService.sync()
        }

        assert(expectedException == resultException)
        verify { cpaSyncService.logFailure(expectedException) }
    }

    private fun mockCpaRepoFromMap(dbCpaMap: Map<String, String>) {
        coEvery { mockCpaRepoClient.getCPATimestamps() } returns dbCpaMap
    }

    private fun mockNfsFromMap(nfsCpaMap: Map<String, String>): NFSConnector {
        val lsEntries = nfsCpaMap.map { mockLsEntry(createFilename(it.key), it.value) }

        return mockNfsFromEntries(lsEntries, nfsCpaMap.keys.toList())
    }

    private fun mockNfsFromEntries(lsEntries: List<ChannelSftp.LsEntry>, nfsCpaIds: List<String>): NFSConnector {
        return mockk {
            every { folder() } returns Vector<ChannelSftp.LsEntry>().apply { addAll(lsEntries) }
            every { file(any()) } returnsMany nfsCpaIds.map { ByteArrayInputStream(simulateFileContent(it).toByteArray()) }
            every { close() } just Runs
        }
    }

    private fun mockLsEntry(entryName: String, timestamp: String): ChannelSftp.LsEntry =
        mockk {
            every { filename } returns entryName
            every { attrs } returns mockSftpAttrs(timestamp)
        }

    private fun mockSftpAttrs(timestamp: String): SftpATTRS = mockk {
        every { mTime } returns Instant.parse(timestamp).epochSecond.toInt()
    }

    private fun createFilename(cpaId: String): String {
        return "${cpaId.replace(":", ".")}.xml"
    }

    private fun simulateFileContent(cpaId: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <cppa:CollaborationProtocolAgreement cppa:cpaid="$cpaId">
                <!-- CPA content removed -->
            </cppa:CollaborationProtocolAgreement>
        """.trimIndent()
    }
}
