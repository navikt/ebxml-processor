package no.nav.emottak.cpa.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommunicationParty(
    @SerialName("herId")
    val herID: Long,
    @SerialName("name")
    val name: String? = null,
    @SerialName("displayName")
    val displayName: String? = null,
    @SerialName("type")
    val type: String? = null,
    @SerialName("organizationDetails")
    val organizationDetails: OrganizationDetails? = null,
    @SerialName("personDetails")
    val personDetails: PersonDetails? = null,
    @SerialName("serviceDetails")
    val serviceDetails: ServiceDetails? = null,
    @SerialName("currentSigningCertificate")
    val currentSigningCertificate: CurrentCertificate? = null,
    @SerialName("currentEncryptionCertificate")
    val currentEncryptionCertificate: CurrentCertificate? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("homepageURL")
    val homepageURL: String? = null,
    @SerialName("phoneNumber")
    val phoneNumber: String? = null,
    @SerialName("faxNumber")
    val faxNumber: String? = null,
    @SerialName("ediAddress")
    val ediAddress: String? = null,
    @SerialName("fhirAddress")
    val fhirAddress: String? = null,
    @SerialName("postalAddress")
    val postalAddress: PostalAddress? = null,
    @SerialName("amqpTransportStatus")
    val amqpTransportStatus: String? = null,
    @SerialName("amqpAddress")
    val amqpAddress: AMQPAddress? = null,
    @SerialName("validFrom")
    val validFrom: String? = null,
    @SerialName("validTo")
    val validTo: String? = null
)

@Serializable
data class AMQPAddress(
    val amqpSyncQueue: String? = null,
    val amqpSyncReplyQueue: String? = null,
    val amqpAsyncQueue: String? = null,
    val amqpErrorQueue: String? = null
)

@Serializable
data class CurrentCertificate(
    val thumbprint: String? = null,
    val validFrom: String? = null,
    val validTo: String? = null
)

@Serializable
data class OrganizationDetails(
    val organizationNumber: String? = null,
    val businessType: Type,
    val persons: List<Long>,
    val services: List<Long>
)

@Serializable
data class Type(
    val codeListID: String? = null,

    val value: String? = null,
    val name: String? = null,
    val url: String? = null
)

@Serializable
data class PersonDetails(
    val hprNumber: Long? = null,
    val parentOrganization: ParentOrganization? = null
)

@Serializable
data class ParentOrganization(
    val name: String? = null,
    val herID: Long,

    val organizationNumber: String? = null
)

@Serializable
data class PostalAddress(
    val address: String? = null,
    val postalBox: String? = null,
    val postalCode: String? = null,
    val city: String? = null
)

@Serializable
data class ServiceDetails(
    val serviceType: Type,
    val interMunicipalityCoverageArea: InterMunicipalityCoverageArea,
    val serviceSpecification: String? = null,
    val parentOrganization: ParentOrganization
)

@Serializable
data class InterMunicipalityCoverageArea(
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
