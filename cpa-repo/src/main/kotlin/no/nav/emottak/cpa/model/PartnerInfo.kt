package no.nav.emottak.cpa.model

import kotlinx.serialization.Serializable

@Serializable
data class CommunicationParty(
    val herId: Long,
    val name: String? = null,
    val displayName: String? = null,
    val type: String? = null,
    val organizationDetails: OrganizationDetails? = null,
    val personDetails: PersonDetails? = null,
    val serviceDetails: ServiceDetails? = null,
    val currentSigningCertificate: CurrentCertificate? = null,
    val currentEncryptionCertificate: CurrentCertificate,
    val email: String? = null,
    val homepageURL: String? = null,
    val phoneNumber: String? = null,
    val faxNumber: String? = null,
    val ediAddress: String? = null,
    val fhirAddress: String? = null,
    val postalAddress: PostalAddress? = null,
    val amqpTransportStatus: String? = null,
    val amqpAddress: AMQPAddress? = null,
    val validFrom: String? = null,
    val validTo: String? = null
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
    val codeListID: String? = null,
    val value: String,
    val name: String? = null,
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
    val municipalityHerIDS: List<Long>
)

@Serializable
data class Certificate(
    val thumbprint: String,
    val validFrom: String,
    val validTo: String,
    val certificateValue: String
)
