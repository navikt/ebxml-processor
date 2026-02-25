package no.nav.emottak.cpa.plugin

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import no.nav.emottak.cpa.model.CommunityPartyResponse
import no.nav.emottak.cpa.model.EncryptionCertificate
import no.nav.emottak.cpa.model.OrganizationDetails
import no.nav.emottak.cpa.model.SignCertificate

object MessagesApi {

    /* =============================================================
     * GET /CommunicationParties/{herId}
     * ============================================================= */

    const val GET_HERID = "/CommunicationParties/{herId}"

    val getHerIdDocs: RouteConfig.() -> Unit = {
        summary = "Get a profile of a partner"
        description = "Get partner information sush as, edi address, certificates and partty name"

        request {
            pathParameter<String>("herId") {
                description = "Her id to the partner"
                required = true

                example("Her ID") {
                    summary = "Multiple receiver HER IDs"
                    description = "At least one receiver HER ID is required"
                    value = "39"
                }
            }

            queryParameter<Int>("herId") {
                description = "Request HER ID"

                example("HER ID") {
                    summary = "Sender filter"
                    description = "HER ID"
                    value = 59
                }
            }

            queryParameter<Boolean>("includeMetadata") {
                description = "Whether to include metadata (default: false)"

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
        }

        response {
            OK to {
                description = """
                    HerId retrieved successfully.
                    Response fields depend on `includeMetadata`.
                """.trimIndent()

                body<CommunityPartyResponse> {
                    example("herId status") {
                        value = listOf(
                            CommunityPartyResponse(
                                herId = 100262,
                                name = "Sykehus HF",
                                parentOrganizationNumber = "983971709",
                                organizationDetails = OrganizationDetails(
                                    organizationNumber = "987654321",
                                    name = "Fysikalsk medisin og rehabilitering"
                                ),
                                email = "ihf@bestill-hos-kundesenter.nhn.no",
                                EncryptionCertificate(
                                    thumbprint = "test by a string",
                                    validFrom = "2023-07-06T08:57:01.429Z",
                                    validTo = "2026-07-06T08:57:01.429Z"
                                ),
                                signCertificate = SignCertificate(
                                    thumbprint = "test by a string",
                                    validFrom = "2023-07-06T08:57:01.429Z",
                                    validTo = "2026-07-06T08:57:01.429Z"
                                )
                            ),
                            CommunityPartyResponse(
                                herId = 59,
                                name = "St. Olavs hospital HF",
                                parentOrganizationNumber = "883974832",
                                organizationDetails = OrganizationDetails(
                                    organizationNumber = "883974832",
                                    name = "Fysioterapi"
                                ),
                                email = "helseplattformen@testedi.nhn.no",
                                EncryptionCertificate(
                                    thumbprint = "test by a string",
                                    validFrom = "2023-08-29T08:57:01.429Z",
                                    validTo = "2026-08-29T08:57:01.429Z"
                                ),
                                signCertificate = SignCertificate(
                                    thumbprint = "test by a string",
                                    validFrom = "2023-08-29T08:57:01.429Z",
                                    validTo = "2026-08-29T08:57:01.429Z"
                                )
                            ),
                            CommunityPartyResponse(
                                herId = 39,
                                name = "Sykehuset Telemark HF",
                                parentOrganizationNumber = "983975267",
                                organizationDetails = OrganizationDetails(
                                    organizationNumber = "983975267",
                                    name = "SYKEHUSET TELEMARK HF TEST"
                                ),
                                email = "test-sthf@edi.nhn.no",
                                EncryptionCertificate(
                                    thumbprint = "test by a string",
                                    validFrom = "2023-07-06T08:57:01.429Z",
                                    validTo = "2026-07-06T08:57:01.429Z"
                                ),
                                signCertificate = SignCertificate(
                                    thumbprint = "test by a string",
                                    validFrom = "2023-07-06T08:57:01.429Z",
                                    validTo = "2026-07-06T08:57:01.429Z"
                                )
                            )
                        )
                    }
                }
            }

            BadRequest to {
                description =
                    "Bad request. Required query parameter `herId` is missing."

                body<String> {
                    example("Missing herId") {
                        summary = "receiverHerIds missing"
                        description =
                            "The mandatory query parameter `herId` was not provided."
                        value = "Receiver her id are missing"
                    }
                }
            }

            NotFound to {
                description = "HerId not found"

                body<String> {
                    example("Not found") {
                        value = "HerId not found"
                    }
                }
            }

            InternalServerError to {
                description = "Internal server error"
            }
        }
    }
}
