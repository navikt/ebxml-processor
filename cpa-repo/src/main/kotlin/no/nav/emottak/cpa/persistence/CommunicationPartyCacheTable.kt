package no.nav.emottak.cpa.persistence

import kotlinx.serialization.KSerializer
import no.nav.emottak.cpa.model.Certificate
import no.nav.emottak.cpa.model.CommunicationParty
import no.nav.emottak.utils.serialization.LENIENT_JSON_PARSER
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import java.time.Instant

object CommunicationPartyCacheTable : Table("communication_party_cache") {
    val herId: Column<Long> = long("her_id")
    val communicationParty: Column<CommunicationParty?> =
        kotlinxJson("communication_party", CommunicationParty.serializer()).nullable()
    val signingCert: Column<Certificate?> =
        kotlinxJson("signing_cert", Certificate.serializer()).nullable()
    val encryptionCert: Column<Certificate?> =
        kotlinxJson("encryption_cert", Certificate.serializer()).nullable()
    val lastUpdated: Column<Instant> = timestamp("last_updated")
    override val primaryKey = PrimaryKey(herId)
}

/**
 * Registers a JSON column serialized/deserialized using kotlinx.serialization, in contrast to the
 * JAXB-based `json()` helper in [CPA] which only supports JAXB-annotated classes.
 */
private fun <T : Any> Table.kotlinxJson(
    name: String,
    serializer: KSerializer<T>
): Column<T> =
    registerColumn(
        name = name,
        type = KotlinxJsonColumnType(serializer)
    )

private class KotlinxJsonColumnType<T : Any>(
    private val serializer: KSerializer<T>
) : ColumnType<T>(false) {
    override fun sqlType(): String = "TEXT"

    override fun valueFromDB(value: Any): T =
        LENIENT_JSON_PARSER.decodeFromString(serializer, value as String)

    override fun notNullValueToDB(value: T): String = LENIENT_JSON_PARSER.encodeToString(serializer, value)

    override fun valueToString(value: T?): String =
        when (value) {
            null -> if (nullable) "NULL" else error("Null value for non-nullable column")
            else -> "'${notNullValueToDB(value)}'"
        }
}
