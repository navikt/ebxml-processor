package no.nav.emottak.cpa.plugin

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import no.nav.emottak.cpa.model.Message
import no.nav.helsemelding.ediadapter.model.EbXmlInfo
import no.nav.helsemelding.ediadapter.model.Metadata
import no.nav.helsemelding.ediadapter.model.OrderBy
import no.nav.helsemelding.ediadapter.model.PostMessageRequest
import kotlin.time.Instant
import kotlin.uuid.Uuid

object MessagesApi {

    /* =============================================================
     * GET /messages
     * ============================================================= */

    const val GET_MESSAGES = "/CommunicationParties"

    val getMessagesDocs: RouteConfig.() -> Unit = {
        summary = "Get a list of unread messages"
        description = "Get a list of unread messages using the given query parameters"

        request {
            queryParameter<List<Int>>("receiverHerIds") {
                description = "List of receiver HER IDs"
                required = true

                example("Multiple receivers") {
                    summary = "Multiple receiver HER IDs"
                    description = "At least one receiver HER ID is required"
                    value = listOf(8142520, 8142521)
                }
            }

            queryParameter<Int>("senderHerId") {
                description = "Sender HER ID"

                example("Sender HER ID") {
                    summary = "Sender filter"
                    description = "Filter messages by sender HER ID"
                    value = 8142519
                }
            }

            queryParameter<String>("businessDocumentId") {
                description = "Business document UUID"

                example("Business document ID") {
                    summary = "Document filter"
                    description = "Filter messages by business document ID"
                    value = "cc169595-bbf0-11dd-9ca9-117f241b4a68"
                }
            }

            queryParameter<Boolean>("includeMetadata") {
                description = "Whether to include message metadata (default: false)"

                example("Default") {
                    summary = "Exclude metadata"
                    description = "Metadata is excluded by default"
                    value = false
                }

                example("Include metadata") {
                    summary = "Include metadata"
                    description = "Returns extended message fields"
                    value = true
                }
            }

            queryParameter<Int>("messagesToFetch") {
                description = "Number of messages to fetch (1–100, default: 10)"

                example("Default") {
                    summary = "Default value"
                    description = "Fetch default number of messages"
                    value = 10
                }

                example("Maximum") {
                    summary = "Maximum value"
                    description = "Fetch the maximum allowed number of messages"
                    value = 100
                }
            }

            queryParameter<OrderBy>("orderBy") {
                description = "Message ordering (default: ASC)"

                example("Ascending") {
                    summary = "Ascending order"
                    description = "Oldest messages first"
                    value = OrderBy.ASC
                }

                example("Descending") {
                    summary = "Descending order"
                    description = "Newest messages first"
                    value = OrderBy.DESC
                }
            }
        }

        response {
            OK to {
                description = """
                    Messages retrieved successfully.
                    Response fields depend on `includeMetadata`.
                """.trimIndent()

                body<List<no.nav.helsemelding.ediadapter.model.Message>> {
                    example("Without metadata") {
                        summary = "Messages without metadata"
                        value = listOf(
                            Message(
                                id = Uuid.parse("733be787-0ad0-475a-98b7-00512caa9ccb"),
                                receiverHerId = 8142520
                            ),
                            Message(
                                id = Uuid.parse("68e60a2b-5990-408c-b99b-089d8657d6ed"),
                                receiverHerId = 8142520
                            )
                        )
                    }

                    example("With metadata") {
                        summary = "Messages with metadata"
                        value = listOf(
                            Message(
                                id = Uuid.parse("733be787-0ad0-475a-98b7-00512caa9ccb"),
                                contentType = "application/xml",
                                receiverHerId = 8142520,
                                senderHerId = 8142519,
                                businessDocumentId = "cc169595-bbf0-11dd-9ca9-117f241b4a68",
                                businessDocumentGenDate = Instant.parse("2008-11-26T19:31:17.281Z"),
                                isAppRec = false,
                                sourceSystem = "helsemelding EDI 2.0 edi-adapter, v1.0" // TODO
                            ),
                            Message(
                                id = Uuid.parse("68e60a2b-5990-408c-b99b-089d8657d6ed"),
                                contentType = "application/xml",
                                receiverHerId = 8142520,
                                senderHerId = 8142519,
                                businessDocumentId = "cc169595-bbf0-11dd-9ca9-117f241b4a68",
                                businessDocumentGenDate = Instant.parse("2008-11-26T19:31:17.281Z"),
                                isAppRec = false,
                                sourceSystem = "helsemelding EDI 2.0 edi-adapter, v1.0" // TODO
                            )
                        )
                    }
                }
            }

            BadRequest to {
                description =
                    "Bad request. Required query parameter `receiverHerIds` is missing."

                body<String> {
                    example("Missing receiverHerIds") {
                        summary = "receiverHerIds missing"
                        description =
                            "The mandatory query parameter `receiverHerIds` was not provided."
                        value = "Receiver her ids are missing"
                    }
                }
            }

            InternalServerError to {
                description = "Unexpected server error"
            }
        }
    }

    /* =============================================================
     * POST /messages
     * ============================================================= */

    const val POST_MESSAGE = "/CommunicationParties"

    val postMessageDocs: RouteConfig.() -> Unit = {
        summary = "Post a new message"
        description =
            "Submits a new message with a business document to one or more receivers."

        request {
            body<PostMessageRequest> {
                required = true

                example("Post message with ebXML overrides") {
                    value = PostMessageRequest(
                        businessDocument =
                        "PHhtbD48RG9jdW1lbnQ+Li4uPC9Eb2N1bWVudD4=",
                        contentType = "application/xml",
                        contentTransferEncoding = "base64",
                        ebXmlOverrides = EbXmlInfo(
                            cpaId = "string",
                            conversationId = "string",
                            service = "string",
                            serviceType = "string",
                            action = "string",
                            useSenderLevel1HerId = true,
                            receiverRole = "string",
                            applicationName = "EPJ Front",
                            applicationVersion = "18.0.8",
                            middlewareName = "string",
                            middlewareVersion = "string",
                            compressPayload = true
                        ),
                        receiverHerIdsSubset = listOf(0)
                    )
                }
            }
        }

        response {
            Created to {
                description = "Message created successfully"

                body<no.nav.helsemelding.ediadapter.model.Metadata> {
                    example("Message metadata") {
                        value = Metadata(
                            id = Uuid.parse("733be787-0ad0-475a-98b7-00512caa9ccb"),
                            location =
                            "https://example.com/communicationparties/733be787-0ad0-475a-98b7-00512caa9ccb"
                        )
                    }
                }
            }

            BadRequest to {
                description = "Invalid request payload"

                body<String> {
                    example("Bad request") {
                        value = "Invalid message payload"
                    }
                }
            }

            InternalServerError to {
                description = "Internal server error"

                body<String> {
                    example("Internal error") {
                        value = "Internal server error"
                    }
                }
            }
        }
    }
}
