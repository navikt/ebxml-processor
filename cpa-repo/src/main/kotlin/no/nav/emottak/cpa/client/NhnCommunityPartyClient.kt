package no.nav.emottak.cpa.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.emottak.cpa.model.CommunityPartyResponse

class NhnCommunityPartyClient(private val accessToken: String) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true // Hindrer krasj hvis NHN legger til nye felt
                    prettyPrint = true
                }
            )
        }
    }

    suspend fun getCommunityParty(herId: Int): CommunityPartyResponse {
        val baseUrl = "https://cpapi.test.grunndata.nhn.no"

        return client.get("$baseUrl/$herId") {
            header("Authorization", "Bearer $accessToken")
            header("Accept", "application/json")
            // NHN krever ofte et kildesystem-navn i headeren
            header("nhn-source-system", "MittSystem-v1.0")
        }.body()
    }
//
//    suspend fun getBaseUrl(baseUrld: String): String {
//        return client.get(baseUrld)
//    }

    fun close() = client.close()
}
