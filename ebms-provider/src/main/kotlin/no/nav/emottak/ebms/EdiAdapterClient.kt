package no.nav.emottak.edi

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.emottak.utils.edi2.models.ApprecInfo
import no.nav.emottak.utils.edi2.models.ErrorMessage
import no.nav.emottak.utils.edi2.models.GetBusinessDocumentResponse
import no.nav.emottak.utils.edi2.models.GetMessagesRequest
import no.nav.emottak.utils.edi2.models.Message
import no.nav.emottak.utils.edi2.models.PostAppRecRequest
import no.nav.emottak.utils.edi2.models.PostMessageRequest
import no.nav.emottak.utils.edi2.models.StatusInfo
import org.slf4j.Logger
import kotlin.uuid.Uuid

class EdiAdapterClient(
    private val ediAdapterUrl: String,
    private val clientProvider: () -> HttpClient,
    private val log: Logger
) {
    private var httpClient = clientProvider.invoke()

    suspend fun getApprecInfo(id: Uuid): Pair<List<ApprecInfo>?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/apprec"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }
        log.debug("EDI2 test: Response from getApprecInfo: $url : ${response.bodyAsText()}")

        return handleResponse(response)
    }

    suspend fun getMessages(getMessagesRequest: GetMessagesRequest): Pair<List<Message>?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages?${getMessagesRequest.toUrlParams()}"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }
        log.debug("EDI2 test: Response from getMessages(): $url : ${response.bodyAsText()}")

        return handleResponse(response)
    }

    suspend fun postMessage(postMessagesRequest: PostMessageRequest): Pair<String?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(postMessagesRequest)
        }
        log.debug("EDI2 test: Response from postMessage() : $url : ${response.bodyAsText()}")

        return handleResponse(response)
    }

    suspend fun getMessage(id: Uuid): Pair<Message?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages/$id"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }
        log.debug("EDI2 test: Response from getMessage() : $url : ${response.bodyAsText()}")

        return handleResponse(response)
    }

    suspend fun getBusinessDocument(id: Uuid): Pair<GetBusinessDocumentResponse?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/document"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }
        log.debug("EDI2 test: Response from getBusinessDocument() : $url : ${response.bodyAsText()}")

        return handleResponse(response)
    }

    suspend fun getMessageStatus(id: Uuid): Pair<List<StatusInfo>?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/status"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }
        log.debug("EDI2 test: Response from getMessageStatus() : $url : ${response.bodyAsText()}")

        return handleResponse(response)
    }

    suspend fun postApprec(id: Uuid, apprecSenderHerId: Int, postAppRecRequest: PostAppRecRequest): Pair<String?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/apprec/$apprecSenderHerId"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(postAppRecRequest)
        }
        log.debug("EDI2 test: Response from postApprec() : $url : ${response.bodyAsText()}")

        return handleResponse(response)
    }

    suspend fun markMessageAsRead(id: Uuid, herId: Int): Pair<Boolean?, ErrorMessage?> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/read/$herId"
        val response = httpClient.put(url) {
            contentType(ContentType.Application.Json)
        }
        log.debug("EDI2 test: Response from markMessageAsRead() : $url : ${response.bodyAsText()}")

        return if (response.status == HttpStatusCode.NoContent) {
            Pair(true, null)
        } else {
            Pair(null, response.body())
        }
    }

    private suspend inline fun <reified T> handleResponse(httpResponse: HttpResponse): Pair<T?, ErrorMessage?> {
        val result: Pair<T?, ErrorMessage?> = if (httpResponse.status == HttpStatusCode.OK || httpResponse.status == HttpStatusCode.Created) {
            Pair(httpResponse.body(), null)
        } else {
            Pair(null, httpResponse.body())
        }
        log.debug("EDI2 test: handleResponse result: {}", result)
        return result
    }
}
