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

        fun getRevocationReason(i: Int): String {
            return try {
                entries[i].name
            } catch (e: IndexOutOfBoundsException) {
                i.toString()
            }
        }
    }
}
