package no.nav.emottak.payload.ocspstatus

enum class RevocationReason {
    Unspecified,
    KeyCompromise,
    CACompromise,
    AffiliationChanged,
    Superseded,
    CessationOfOperation,
    CertificateHold;

    companion object {
        private fun valueOf(i: Int): RevocationReason {
            values().forEach {
                if (i == it.ordinal) {
                    return it
                }
            }
            throw IllegalArgumentException("no RevocationReason for ordinal $i")
        }

        fun getRevocationReason(i: Int): String {
            return try {
                valueOf(i).name
            } catch (e: IllegalArgumentException) {
                i.toString()
            }
        }
    }

}

