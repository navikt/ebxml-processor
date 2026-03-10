package no.nav.emottak.cpa.model

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val klaxon = Klaxon()

class CommunicationParty(elements: Collection<CommunicationPartyElement>) : ArrayList<CommunicationPartyElement>(elements) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = CommunicationParty(klaxon.parseArray<CommunicationPartyElement>(json)!!)
    }
}

@Serializable
data class CommunicationPartyElement(
    @Json(name = "herId")
    val herID: Long,

    val name: String,
    val displayName: String,
    val type: String,
    val organizationDetails: OrganizationDetails,
    val personDetails: PersonDetails,
    val serviceDetails: ServiceDetails,
    val currentSigningCertificate: CurrentCertificate,
    val currentEncryptionCertificate: CurrentCertificate,
    val email: String,

    @Json(name = "homepageUrl")
    val homepageURL: String,

    val phoneNumber: String,
    val faxNumber: String,
    val ediAddress: String,
    val fhirAddress: String,
    val postalAddress: PostalAddress,
    val amqpTransportStatus: String,
    val amqpAddress: AMQPAddress,
    val validFrom: String,
    val validTo: String
)

@Serializable
data class AMQPAddress(
    val amqpSyncQueue: String,
    val amqpSyncReplyQueue: String,
    val amqpAsyncQueue: String,
    val amqpErrorQueue: String
)

@Serializable
data class CurrentCertificate(
    val thumbprint: String,
    val validFrom: String,
    val validTo: String
)

@Serializable
data class OrganizationDetails(
    val organizationNumber: String,
    val businessType: Type,
    val persons: List<Long>,
    val services: List<Long>
)

@Serializable
data class Type(
    @Json(name = "codeListId")
    val codeListID: String,

    val value: String,
    val name: String,
    val url: String
)

@Serializable
data class PersonDetails(
    val hprNumber: Long,
    val parentOrganization: ParentOrganization
)

@Serializable
data class ParentOrganization(
    val name: String,

    @Json(name = "herId")
    val herID: Long,

    val organizationNumber: String
)

@Serializable
data class PostalAddress(
    val address: String,
    val postalBox: String,
    val postalCode: String,
    val city: String
)

@Serializable
data class ServiceDetails(
    val serviceType: Type,
    val interMunicipalityCoverageArea: InterMunicipalityCoverageArea,
    val serviceSpecification: String,
    val parentOrganization: ParentOrganization
)

@Serializable
data class InterMunicipalityCoverageArea(
    @Json(name = "municipalityHerIds")
    val municipalityHerIDS: List<Long>
)

@Serializable
data class Certificate(
    @SerialName("thumbprint")
    val thumbprint: String? = null,
    @SerialName("validFrom")
    val validFrom: String? = null,
    @SerialName("validTo")
    val validTo: String? = null,
    @SerialName("certificateValue")
    val certificateValue: String? = null
)
