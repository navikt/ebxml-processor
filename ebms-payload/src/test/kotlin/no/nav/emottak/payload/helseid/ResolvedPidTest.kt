package no.nav.emottak.payload.helseid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class ResolvedPidTest {
    @ParameterizedTest
    @EnumSource(PidSource::class)
    fun `toString masks PID and retains source`(source: PidSource) {
        val resolvedPid = ResolvedPid("01010112345", source)

        assertEquals("ResolvedPid(pid=010*******5, source=$source)", resolvedPid.toString())
        assertFalse(resolvedPid.toString().contains(resolvedPid.pid))
        assertEquals("01010112345", resolvedPid.pid)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "1", "12", "123", "1234", "12345"])
    fun `toString fully masks short PIDs`(pid: String) {
        val resolvedPid = ResolvedPid(pid, PidSource.OCSP)

        assertEquals("ResolvedPid(pid=${"*".repeat(pid.length)}, source=OCSP)", resolvedPid.toString())
    }
}
