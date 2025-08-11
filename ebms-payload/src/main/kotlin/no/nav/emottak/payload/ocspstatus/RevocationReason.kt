package no.nav.emottak.payload.ocspstatus

enum class RevocationReason {
    UNSPECIFIED,
    KEY_COMPROMISE,
    CA_COMPROMISE,
    AFFILIATION_CHANGED,
    SUPERSEDED,
    CESSATION_OF_OPERATION,
    CERTIFICATE_HOLD;

    companion object {

        fun getRevocationReason(i: Int): String {
            return try {
                entries[i].name
            } catch (e: IndexOutOfBoundsException) {
                i.toString()
            }
        }
    }
}
