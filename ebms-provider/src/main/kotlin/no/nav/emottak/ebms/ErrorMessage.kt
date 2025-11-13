package no.nav.emottak.edi

import kotlinx.serialization.Serializable

@Serializable
data class ErrorMessage(
    val error: String? = null,
    val errorCode: Int,
    val validationErrors: List<String>? = null,
    val stackTrace: String? = null,
    val requestId: String
)
