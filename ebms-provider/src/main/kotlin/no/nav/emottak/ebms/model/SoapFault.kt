package no.nav.emottak.ebms.model

import kotlinx.serialization.Serializable

@Serializable
data class SoapFault(val faultCode: String, val faultMessage: String)
