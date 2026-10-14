package no.nav.emottak.payload.helseid

data class ResolvedPid(
    val pid: String,
    val source: PidSource
) {
    val maskedPid: String
        get() = if (pid.length <= 5) "*".repeat(pid.length) else pid.replaceRange(3, pid.length - 1, "*".repeat(pid.length - 4))

    override fun toString(): String = "ResolvedPid(pid=$maskedPid, source=$source)"
}

enum class PidSource {
    HelseID,
    OCSP
}
