package no.nav.emottak.cpa.model

import java.util.Date

data class CurrentEncryptionCertificate(
    val thumbprint: String,
    val validFrom: Date,
    val validTo: Date,
    val certificateValue: String
)
