package no.nav.emottak.cpa

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import no.nav.emottak.cpa.model.Certificate
import no.nav.emottak.cpa.model.CommunicationParty
import no.nav.emottak.cpa.model.OrganizationDetails

fun getFakeNhnAdresseregisterEngine(): MockEngine =
    MockEngine { request ->
        val responseBody = when {
            request.url.encodedPath.endsWith("/api/v1/communicationparty/1") -> {
                var communicationParty = CommunicationParty(
                    79768,
                    "NAV IKT",
                    OrganizationDetails("889640782"),
                    "mottak-qass@test-es.nav.no",
                    "2020-01-06T23:23:59+00:00",
                    "01.01.2030T23:23:59+00:00"
                )
                Json.encodeToString(communicationParty)
            }
            request.url.encodedPath.contains("/api/v1/certificate/1/signing") -> {
                val currentSigning = "MIIGNDCCBBygAwIBAgIKa78z60lFRxXAdDANBgkqhkiG9w0BAQsFADBoMQswCQYDVQQGEwJOTzEYMBYGA1UEYQwPTlRSTk8tOTgzMTYzMzI3MRMwEQYDVQQKDApCdXlwYXNzIEFTMSowKAYDVQQDDCFCdXlwYXNzIENsYXNzIDMgQ0EgRzIgU1QgQnVzaW5lc3MwHhcNMjUwODA2MTMyNzU1WhcNMjgwODA2MjE1OTAwWjBxMQswCQYDVQQGEwJOTzEjMCEGA1UECgwaQVJCRUlEUy0gT0cgVkVMRkVSRFNFVEFURU4xIzAhBgNVBAMMGkFSQkVJRFMtIE9HIFZFTEZFUkRTRVRBVEVOMRgwFgYDVQRhDA9OVFJOTy04ODk2NDA3ODIwggGiMA0GCSqGSIb3DQEBAQUAA4IBjwAwggGKAoIBgQC3VWnVUj50XSRw+Mf316qahVBcJHLu9vhys4SbpJFoEiRm03gpKomgL79RxuHgk2Vww/FfaqmFNhIsIy/VpEDUnqpHG7djn1JvxZsJPd+hxJdKApQ2Vrfl7urV+prZEjCs2ol1pwmctO30KMM1+o7Qd2r6n9D1darg04cJUwxAPK+rEZn/rtdBMXX0UWXKESQ6adXBVxuby93ZLQ/z4+me8UaoCDCLpbHEmj8fcbkwiQQs47WaqLBhNw1FQuIKioNxWQV6Zl/1O+Ir1hJ7V7dY1dsdcXB+eHd85UU71RfnMkZos7ASlwx7J2hb6EelBIG37ud7fslx93xzAxFnWrGxSw4PFrUqONpvQMGDYmOksdTjyT3QKtxGULkMvh+2Snj4F6fquA01UdWx3pLBHPRVTiz53SdVRVe3OBNWtji2f/vSBP++n7cgY12cS5u/UcwnhgEJSPC/0fg+E0LcpY++C3De2JvSfcuYy6DHxSkX1skqUD8MorZ8QaSY60i3HtMCAwEAAaOCAVUwggFRMAkGA1UdEwQCMAAwHwYDVR0jBBgwFoAUgUWcKZVmODftO1ROjjLlliF51WQwHQYDVR0OBBYEFHVXU9Opuw6ij6+3kGTkD9U"
                var signCertificate = Certificate(
                    "D2A7137640A4CE620410C53A7BEDE4D00A3FC611",
                    "2026-01-06T23:00:00+00:00",
                    "2029-01-06T23:23:59+00:00",
                    currentSigning
                )

                Json.encodeToString(signCertificate)
            }
            request.url.encodedPath.contains("/api/v1/certificate/1/encryption") -> {
                val encryptCertificate = "MIIGNDCCBBygAwIBAgIKa78z60lFRxXAdDANBgkqhkiG9w0BAQsFADBoMQswCQYDVQQGEwJOTzEYMBYGA1UEYQwPTlRSTk8tOTgzMTYzMzI3MRMwEQYDVQQKDApCdXlwYXNzIEFTMSowKAYDVQQDDCFCdXlwYXNzIENsYXNzIDMgQ0EgRzIgU1QgQnVzaW5lc3MwHhcNMjUwODA2MTMyNzU1WhcNMjgwODA2MjE1OTAwWjBxMQswCQYDVQQGEwJOTzEjMCEGA1UECgwaQVJCRUlEUy0gT0cgVkVMRkVSRFNFVEFURU4xIzAhBgNVBAMMGkFSQkVJRFMtIE9HIFZFTEZFUkRTRVRBVEVOMRgwFgYDVQRhDA9OVFJOTy04ODk2NDA3ODIwggGiMA0GCSqGSIb3DQEBAQUAA4IBjwAwggGKAoIBgQC3VWnVUj50XSRw+Mf316qahVBcJHLu9vhys4SbpJFoEiRm03gpKomgL79RxuHgk2Vww/FfaqmFNhIsIy/VpEDUnqpHG7djn1JvxZsJPd+hxJdKApQ2Vrfl7urV+prZEjCs2ol1pwmctO30KMM1+o7Qd2r6n9D1darg04cJUwxAPK+rEZn/rtdBMXX0UWXKESQ6adXBVxuby93ZLQ/z4+me8UaoCDCLpbHEmj8fcbkwiQQs47WaqLBhNw1FQuIKioNxWQV6Zl/1O+Ir1hJ7V7dY1dsdcXB+eHd85UU71RfnMkZos7ASlwx7J2hb6EelBIG37ud7fslx93xzAxFnWrGxSw4PFrUqONpvQMGDYmOksdTjyT3QKtxGULkMvh+2Snj4F6fquA01UdWx3pLBHPRVTiz53SdVRVe3OBNWtji2f/vSBP++n7cgY12cS5u/UcwnhgEJSPC/0fg+E0LcpY++C3De2JvSfcuYy6DHxSkX1skqUD8MorZ8QaSY60i3HtMCAwEAAaOCAVUwggFRMAkGA1UdEwQCMAAwHwYDVR0jBBgwFoAUgUWcKZVmODftO1ROjjLlliF51WQwHQYDVR0OBBYEFHVXU9Opuw6ij6+3kGTkD9U"
                var signCertificate = Certificate(
                    "P7A09812340A4CE620410C53A7BEDE4G786QTY98",
                    "2025-11-01T23:00:00+00:00",
                    "2028-11-01T23:23:59+00:00",
                    encryptCertificate
                )

                Json.encodeToString(signCertificate)
            }
            else -> """{"error": "not found"}"""
        }

        respond(
            content = ByteReadChannel(responseBody),
            status = if (responseBody.contains("error")) HttpStatusCode.NotFound else HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
