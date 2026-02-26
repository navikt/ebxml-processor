package no.nav.emottak.cpa.model

import java.util.Date;

data class CurrentSigningCertificat(
    val thumbprint: String,
    val validFrom: Date,
    val validTo: Date,
    val certificateValue: String
)
