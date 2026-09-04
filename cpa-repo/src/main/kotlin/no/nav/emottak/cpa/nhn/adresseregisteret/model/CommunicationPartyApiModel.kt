package no.nav.emottak.cpa.nhn.adresseregisteret.model

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
    val currentEncryptionCertificate: CurrentCertificate? = null,
    val email: String? = null,
    val homepageUrl: String? = null,
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
    val validFrom: String? = null,
    val validTo: String? = null
)

@Serializable
data class OrganizationDetails(
    val organizationNumber: String? = null,
    val businessType: Type,
    val persons: List<Long>? = null,
    val services: List<Long>? = null
)

@Serializable
data class Type(
    val codeListId: String? = null,
    val value: String,
    val name: String? = null,
    val url: String? = null
)

@Serializable
data class PersonDetails(
    val hprNumber: Long,
    val parentOrganization: ParentOrganization
)

@Serializable
data class ParentOrganization(
    val name: String,
    val herId: Long,
    val organizationNumber: String? = null
)

@Serializable
data class PostalAddress(
    val address: String? = null,
    val postalBox: String? = null,
    val postalCode: String,
    val city: String
)

@Serializable
data class ServiceDetails(
    val serviceType: Type,
    val interMunicipalityCoverageArea: InterMunicipalityCoverageArea? = null,
    val serviceSpecification: String? = null,
    val parentOrganization: ParentOrganization
)

@Serializable
data class InterMunicipalityCoverageArea(
    val municipalityHerIds: List<Long>? = null
)

@Serializable
data class Certificate(
    val thumbprint: String,
    val validFrom: String? = null,
    val validTo: String? = null,
    val certificateValue: String
)
