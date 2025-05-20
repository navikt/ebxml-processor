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

        fun toString(i: Int): String =
            try {
                RevocationReason.Companion.valueOf(i).name
            } catch (e: IllegalArgumentException) {
                i.toString()
            }

        fun valueOf(i: Int): RevocationReason {
            for (rc in RevocationReason.entries) {
                if (i == rc.ordinal) {
                    return rc
                }
            }
            throw IllegalArgumentException("no RevocationReason for ordinal $i")
        }

    }

}
