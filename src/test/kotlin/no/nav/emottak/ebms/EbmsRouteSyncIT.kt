package no.nav.emottak.ebms

import io.ktor.client.request.post
import io.ktor.client.statement.readBytes
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkStatic
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.PayloadMessage
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.ebms.validation.MimeHeaders
import no.nav.emottak.message.model.Addressing
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.EbmsProcessing
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.Party
import no.nav.emottak.message.model.PartyId
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.SendInResponse
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.util.decodeBase64
import no.nav.emottak.util.getEnvVar
import org.apache.xml.security.algorithms.MessageDigestAlgorithm
import org.apache.xml.security.signature.XMLSignature
import org.junit.jupiter.api.Test
import java.util.*

private const val SYNC_PATH = "/ebms/sync"

class EbmsRouteSyncIT : EbmsRoutFellesIT(SYNC_PATH) {

    fun <T> testSyncApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val cpaRepoClient = CpaRepoClient { client }
        val sendInClient = SendInClient { client }
        application {
            val dokumentValidator = DokumentValidator(cpaRepoClient)
            coEvery {
                processingService.processSyncIn(any(), any())
            } answers {
                Pair(it.invocation.args[0] as PayloadMessage, Direction.IN)
            }
            coEvery {
                processingService.proccessSyncOut(any(), any())
            } answers {
                val incomingMessage = it.invocation.args[0] as PayloadMessage
                incomingMessage
            }
            routing {
                postEbmsSync(dokumentValidator, processingService, SendInService(sendInClient))
                postEbmsAsync(dokumentValidator, processingService)
            }
        }
        externalServices {
            hosts(getEnvVar("CPA_REPO_URL", "http://cpa-repo.team-emottak.svc.nais.local")) {
                this.install(ContentNegotiation) {
                    json()
                }
                routing {
                    post("cpa/validate/soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1") {
                        call.respond(ValidationResult(error = listOf(Feil(ErrorCode.SECURITY_FAILURE, "Signature Fail"))))
                    }
                    post("cpa/validate/{contentId}") {
                        val contentId = call.parameters["contentId"]
                        if (contentId == "contentID-validRequest") {
                            call.respond(
                                ValidationResult(
                                    EbmsProcessing(),
                                    payloadProcessing = PayloadProcessing(
                                        TestData.HarBorderEgenAndel.validSignatureDetails,
                                        byteArrayOf(),
                                        mockProcessConfig
                                    )
                                )
                            )
                        } else {
                            call.respond(
                                ValidationResult(
                                    EbmsProcessing(),
                                    payloadProcessing = PayloadProcessing(
                                        SignatureDetails(byteArrayOf(), "test", "test"),
                                        byteArrayOf(),
                                        mockProcessConfig
                                    )
                                )
                            )
                        }
                    }
                }
            }
            hosts(getEnvVar("SEND_IN_URL", "http://ebms-send-in")) {
                this.install(ContentNegotiation) {
                    json()
                }
                routing {
                    post("/fagmelding/synkron") {
                        call.respond(
                            SendInResponse(
                                "e17eb03e-9e43-43fb-874c-1fde9a28c308",
                                "1234",
                                Addressing(Party(listOf(PartyId("HER", "79768")), "Frikortregister"), Party(listOf(PartyId("HER", "8090595")), "Utleverer"), "HarBorgerEgenandelFritak", "EgenandelSvar"),
                                this::class.java.classLoader.getResource("har-borger-egenandrel-fritak/harBorgerEgenandelFritakResponseFagmelding.xml")!!.readBytes()
                            )
                        )
                    }
                }
            }
        }
        testBlock()
    }

    @Test
    fun `Valid payload request should trigger processing and validation on way out`() = testSyncApp {
        mockkStatic(EbMSDocument::signer)
        every {
            any<EbMSDocument>().signer(any())
        } returnsArgument(0)
        val multipart = TestData.HarBorderEgenAndel.harBorgerEgenanderFritakRequest
        val response = client.post(SYNC_PATH, multipart.asHttpRequest())
        coVerify(exactly = 1) {
            processingService.processSyncIn(any(), any())
        }
        assert(response.status == HttpStatusCode.OK)
        println(String(response.readBytes()))
        println("----=_Part_" + System.currentTimeMillis() + "." + System.nanoTime())
        println("----=_Part_" + UUID.randomUUID().toString())
    }

    @Test
    fun `Feilmelding fra fagsystemet må propageres til brukeren`() = testSyncApp {
        val soapFault = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><SOAP-ENV:Fault><faultcode>SOAP-ENV:Server</faultcode><faultstring>Noe gikk galt i fagsystemet</faultstring></SOAP-ENV:Fault></SOAP-ENV:Body></SOAP-ENV:Envelope>"
        mockkStatic(EbMSDocument::signer)
        every {
            any<EbMSDocument>().signer(any())
        } returnsArgument(0)
        externalServices {
            hosts(getEnvVar("SEND_IN_URL", "http://ebms-send-in")) {
                this.install(ContentNegotiation) {
                    json()
                }
                routing {
                    post("/fagmelding/synkron") {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Noe gikk galt i fagsystemet"
                        )
                    }
                }
            }
        }
        val multipart = TestData.HarBorderEgenAndel.harBorgerEgenanderFritakRequest
        val response = client.post(SYNC_PATH, multipart.asHttpRequest())
        coVerify(exactly = 1) {
            processingService.processSyncIn(any(), any())
        }
        assert(response.status == HttpStatusCode.InternalServerError)
        assert(String(response.readBytes()) == soapFault)
        println("----=_Part_" + System.currentTimeMillis() + "." + System.nanoTime())
        println("----=_Part_" + UUID.randomUUID().toString())
    }
}

internal class TestData {

    class HarBorderEgenAndel {
        companion object {
            val MULTIPART_CONTENT_TYPE =
                """multipart/related; boundary="------=_part_c2a3282c_e754_4193_9fb5_e8a469941f97"; type="text/xml"; start="soapId-c1a5d7ed-3850-4a74-ba28-8d73f5355336"""
            val EBXML_PAYLOAD =
                """<?xml version="1.0" encoding="UTF-8"?><SOAP:Envelope xsi:schemaLocation="http://schemas.xmlsoap.org/soap/envelope/ http://www.oasis-open.org/committees/ebxml-msg/schema/envelope.xsd http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd" xmlns:eb="http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:SOAP="http://schemas.xmlsoap.org/soap/envelope/"><SOAP:Header><eb:MessageHeader eb:version="2.0" SOAP:mustUnderstand="1"><eb:From><eb:PartyId eb:type="HER">8090595</eb:PartyId><eb:Role>Utleverer</eb:Role></eb:From><eb:To><eb:PartyId eb:type="HER">79768</eb:PartyId><eb:Role>Frikortregister</eb:Role></eb:To><eb:CPAId>nav:qass:31162</eb:CPAId><eb:ConversationId>e17eb03e-9e43-43fb-874c-1fde9a28c308</eb:ConversationId><eb:Service eb:type="string">HarBorgerEgenandelFritak</eb:Service><eb:Action>EgenandelForesporsel</eb:Action><eb:MessageData><eb:MessageId>e17eb03e-9e43-43fb-874c-1fde9a28c308</eb:MessageId><eb:Timestamp>2024-02-12T13:04:02.541Z</eb:Timestamp></eb:MessageData></eb:MessageHeader><eb:SyncReply eb:version="2.0" SOAP:mustUnderstand="1" SOAP:actor="http://schemas.xmlsoap.org/soap/actor/next"/><Signature xmlns="http://www.w3.org/2000/09/xmldsig#"><SignedInfo><CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/><SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1"/><Reference URI=""><Transforms><Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/><Transform Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/></Transforms><DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1"/><DigestValue>8soClBWhwJWIJi4uo0BvfN/tNlY=</DigestValue></Reference><Reference URI="cid:attachmentId-3b407d6f-7efc-4ce9-99a6-868f04329e68"><DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1"/><DigestValue>ch2SFbU7KFmNm6Jp5zWa1dGVWCQ=</DigestValue></Reference></SignedInfo><SignatureValue>Q0Op2SZFDiQqp+zt0GX9mZ8Q8LGqIKmAsfc+l9X7mlgmKtOyQ0MBDo4ebzoRX4GLviWBkF2TMTRvWluw87blUJSga2I226yVC4GaTjhNhP50Rkvxd9olhHWSs/E/JrpT/X7KFmVDAR5A+mRxPKFMaKDkS32IHlcyi6x8jA2HSUZxD1pkfALj2B8EuL/39e5BecfOkHOyHzj/Nz0gvIgldYKhaTf81GAgDYuQ/STggXtJ7FbsVeXjIN9mdJyrq4DdO11StMS+ET5WQrtn5egRkzzuDLtwYL4IB4oFSZ8bYr2hNcFhWYYq5IvKWCU93AlKktbIYaZ10AtigYUYQTkBFWlqgHPhsN+5IDcqgXguJTSEh9pbgKf4W1RzuzgjxNvPCAV5D7LAyVp1QLG+n6hUgFYRO0QY1LkCZ+Vs7YJrgbBZyjwjBq1gZz6Y9B4jHBPBpjKFxN/0p4CGJ5G8YTumMMPwuxyP8XiYnKShF/IzM7VAOfwYmhnm/mw2mEItmly3</SignatureValue><KeyInfo><X509Data><X509Certificate>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</X509Certificate></X509Data><KeyValue><RSAKeyValue><Modulus>1HE+rxisqKWgFOnqAtyig+2vpadevrgTfbhQsZ4A0tevblgURDat2y5cMMqOI5FAXym+0KGooCdupZZGb1G9mmII5/clW2FkeeqvVBcJ5WCUynEuYd+v6dhMAdvgc3ZNgdlqey3amXyhF9YWkQPoaBLyfIjYzsBak3A5FfAUmcCAt3Fh2k7SkfvGypYVVEDeKBFnD/sxAuWBgolc+go+ylvSZdHbAEsaHGJAXBz3fTFKkPwng8dRPQRYCd34/FcKXdl15U2CdOLkMiqy4+qQRckJFKppa40COR7WVStJ3Tq41VqxDrPeqoigUySmNQ2g27nXS7EpPfKoKr/MSVo92euna4yLtKXnbUi9vPcmnb6BQn0CNe4YesHCq7Evt7Cc0ksxBzxN4YXNkCD+EonMnTL0d5K937Pm6QCF0UMHE55UmrCXKYAPsKkhmicfVz7+nhg5+ZDKmjY6rjNC2ro23Jy6B9jdu7v4/J+55Tsq+XWrcgX6RD6M4WV4HDH7HkTr</Modulus><Exponent>AQAB</Exponent></RSAKeyValue></KeyValue></KeyInfo></Signature></SOAP:Header><SOAP:Body><eb:Manifest eb:version="2.0"><eb:Reference xlink:href="cid:attachmentId-3b407d6f-7efc-4ce9-99a6-868f04329e68"/></eb:Manifest></SOAP:Body></SOAP:Envelope>"""
            val FAGMELDING_PAYLOAD = """PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0idXRmLTgiPz48bnM6TXNnSGVhZCB4bWxuczp4
c2Q9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hIiB4bWxuczp4c2k9Imh0dHA6Ly93
d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hLWluc3RhbmNlIiB4bWxuczpucz0iaHR0cDovL3d3dy5r
aXRoLm5vL3htbHN0ZHMvbXNnaGVhZC8yMDA2LTA1LTI0IiB4c2k6c2NoZW1hTG9jYXRpb249Imh0
dHA6Ly93d3cua2l0aC5uby94bWxzdGRzL21zZ2hlYWQvMjAwNi0wNS0yNCBNc2dIZWFkLXYxXzIu
eHNkIj4KICA8bnM6TXNnSW5mbz4KICAgIDxuczpUeXBlIFY9IkVnZW5hbmRlbEZvcmVzcG9yc2Vs
VjIiIEROPSJGb3Jlc3DDuHJzZWwgb20gZWdlbmFuZGVsIiAvPgogICAgPG5zOk1JR3ZlcnNpb24+
djEuMiAyMDA2LTA1LTI0PC9uczpNSUd2ZXJzaW9uPgogICAgPG5zOkdlbkRhdGU+MjAyNC0wMi0x
MlQxMzowNDowMi41NDFaPC9uczpHZW5EYXRlPgogICAgPG5zOk1zZ0lkPmUxN2ViMDNlLTllNDMt
NDNmYi04NzRjLTFmZGU5YTI4YzMwODwvbnM6TXNnSWQ+CiAgICA8bnM6Q29udmVyc2F0aW9uUmVm
PgogICAgICA8bnM6UmVmVG9QYXJlbnQ+ZTE3ZWIwM2UtOWU0My00M2ZiLTg3NGMtMWZkZTlhMjhj
MzA4PC9uczpSZWZUb1BhcmVudD4KICAgICAgPG5zOlJlZlRvQ29udmVyc2F0aW9uPmUxN2ViMDNl
LTllNDMtNDNmYi04NzRjLTFmZGU5YTI4YzMwODwvbnM6UmVmVG9Db252ZXJzYXRpb24+CiAgICA8
L25zOkNvbnZlcnNhdGlvblJlZj4KICAgIDxuczpTZW5kZXI+CiAgICAgIDxuczpPcmdhbmlzYXRp
b24+CiAgICAgICAgPG5zOk9yZ2FuaXNhdGlvbk5hbWU+VEVTVCBBMSBIYXVnZXJ1ZDwvbnM6T3Jn
YW5pc2F0aW9uTmFtZT4KICAgICAgICA8bnM6SWRlbnQ+CiAgICAgICAgICA8bnM6SWQ+OTg0ODg2
MTM2PC9uczpJZD4KICAgICAgICAgIDxuczpUeXBlSWQgVj0iRU5IIiBTPSIyLjE2LjU3OC4xLjEy
LjQuMS4xLjkwNTEiIEROPSJPcmdhbmlzYXNqb25zbnVtbWVyZXQgaSBFbmhldHNyZWdpc3RlciIg
Lz4KICAgICAgICA8L25zOklkZW50PgogICAgICAgIDxuczpJZGVudD4KICAgICAgICAgIDxuczpJ
ZD44MDkwNTk1PC9uczpJZD4KICAgICAgICAgIDxuczpUeXBlSWQgVj0iSEVSIiBTPSIyLjE2LjU3
OC4xLjEyLjQuMS4xLjkwNTEiIEROPSJIRVItaWQiIC8+CiAgICAgICAgPC9uczpJZGVudD4KICAg
ICAgPC9uczpPcmdhbmlzYXRpb24+CiAgICA8L25zOlNlbmRlcj4KICAgIDxuczpSZWNlaXZlcj4K
ICAgICAgPG5zOk9yZ2FuaXNhdGlvbj4KICAgICAgICA8bnM6T3JnYW5pc2F0aW9uTmFtZT5OQVY8
L25zOk9yZ2FuaXNhdGlvbk5hbWU+CiAgICAgICAgPG5zOklkZW50PgogICAgICAgICAgPG5zOklk
Pjg4OTY0MDc4MjwvbnM6SWQ+CiAgICAgICAgICA8bnM6VHlwZUlkIFY9IkVOSCIgUz0iMi4xNi41
NzguMS4xMi40LjEuMS45MDUxIiBETj0iT3JnYW5pc2Fzam9uc251bW1lcmV0IGkgRW5oZXRzcmVn
aXN0ZXIiIC8+CiAgICAgICAgPC9uczpJZGVudD4KICAgICAgICA8bnM6SWRlbnQ+CiAgICAgICAg
ICA8bnM6SWQ+Nzk3Njg8L25zOklkPgogICAgICAgICAgPG5zOlR5cGVJZCBWPSJIRVIiIFM9IjIu
MTYuNTc4LjEuMTIuNC4xLjEuOTA1MSIgRE49IklkZW50aWZpa2F0b3IgZnJhIEhlbHNldGplbmVz
dGVlbmhldHNyZWdpc3RlcmV0IiAvPgogICAgICAgIDwvbnM6SWRlbnQ+CiAgICAgIDwvbnM6T3Jn
YW5pc2F0aW9uPgogICAgPC9uczpSZWNlaXZlcj4KICA8L25zOk1zZ0luZm8+CiAgPG5zOkRvY3Vt
ZW50PgogICAgPG5zOkRvY3VtZW50Q29ubmVjdGlvbiBWPSJIIiBETj0iSG92ZWRkb2t1bWVudCIg
Lz4KICAgIDxuczpSZWZEb2M+CiAgICAgIDxuczpNc2dUeXBlIFY9IlhNTCIgRE49IlhNTC1pbnN0
YW5zIiAvPgogICAgICA8bnM6Q29udGVudD4KICAgICAgICA8RWdlbmFuZGVsRm9yZXNwb3JzZWxW
MiB4bWxucz0iaHR0cDovL3d3dy5raXRoLm5vL3htbHN0ZHMvbmF2L2VnZW5hbmRlbC8yMDE2LTA2
LTEwIj4KICAgICAgICAgIDxIYXJCb3JnZXJFZ2VuYW5kZWxmcml0YWs+CiAgICAgICAgICAgIDxC
b3JnZXJGbnI+MDgwNTA0Njc0MTU8L0JvcmdlckZucj4KICAgICAgICAgICAgPERhdG8+MjAyNC0w
Mi0xMjwvRGF0bz4KICAgICAgICAgIDwvSGFyQm9yZ2VyRWdlbmFuZGVsZnJpdGFrPgogICAgICAg
IDwvRWdlbmFuZGVsRm9yZXNwb3JzZWxWMj4KICAgICAgPC9uczpDb250ZW50PgogICAgPC9uczpS
ZWZEb2M+CiAgPC9uczpEb2N1bWVudD4KPC9uczpNc2dIZWFkPg=="""

            val valid = Headers.build {
                append(MimeHeaders.MIME_VERSION, "1.0")
                append(MimeHeaders.SOAP_ACTION, "ebXML")
                append(MimeHeaders.CONTENT_TYPE, MULTIPART_CONTENT_TYPE)
                append(SMTPHeaders.MESSAGE_ID, "1234")
            }

            val validSoapMimeHeaders = Headers.build {
                append(MimeHeaders.CONTENT_ID, "<contentID-validRequest>")
                append(MimeHeaders.CONTENT_TRANSFER_ENCODING, "binary")
                append(MimeHeaders.CONTENT_TYPE, """text/xml; charset="UTF-8""")
            }

            val validSoapAttachmentHeaders = Headers.build {
                append(MimeHeaders.CONTENT_ID, "<attachmentId-3b407d6f-7efc-4ce9-99a6-868f04329e68>")
                append(MimeHeaders.CONTENT_TRANSFER_ENCODING, "base64")
                append(MimeHeaders.CONTENT_TYPE, """application/pkcs7-mime""")
                append(MimeHeaders.CONTENT_DISPOSITION, "attachment")
            }
            val harBorgerEgenanderFritakRequest = MultipartRequest(
                valid,
                listOf(
                    Part(validSoapMimeHeaders, EBXML_PAYLOAD),
                    Part(validSoapAttachmentHeaders, FAGMELDING_PAYLOAD)
                )
            )

            val validSignatureDetails = SignatureDetails(
                decodeBase64("""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""".toByteArray()),
                signatureAlgorithm = XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                hashFunction = MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256
            )
        }
    }
}
