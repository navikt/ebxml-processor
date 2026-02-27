package no.nav.emottak.cpa

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

fun getFakeNhnAdresseregisterEngine(content: String = "test"): MockEngine =
    MockEngine { _ ->
        respond(
            content = content,
            status = HttpStatusCode.OK,
            headers = io.ktor.http.headers {
                append(HttpHeaders.ContentType, "application/json")
            }
        )
    }
