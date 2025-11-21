package no.nav.emottak.cpa.persistence

import no.nav.emottak.cpa.marshal
import no.nav.emottak.cpa.unmarshal
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement

object CPA : Table("cpa") {
    val id: Column<String> = varchar("cpa_id", 256)
    val herId: Column<String?> = varchar("herid", 50).nullable()
    val cpa = json("cpa", CollaborationProtocolAgreement::class.java)
    val updated_date = timestamp("updated_date")
    val entryCreated = timestamp("create_date")
    val lastUsed: Column<java.time.Instant?> = timestamp("last_used").nullable()
}

fun <T : Any> Table.json(
    name: String,
    clazz: Class<T>,
    nullable: Boolean = false
): Column<T> =
    registerColumn(
        name = name,
        type = JsonColumnType(clazz, nullable)
    )

class JsonColumnType<T : Any>(
    private val clazz: Class<T>,
    nullable: Boolean
) : ColumnType<T>(nullable) {
    override fun sqlType(): String = "json"

    override fun valueFromDB(value: Any): T = unmarshal(value as String, clazz)

    override fun notNullValueToDB(value: T): String = marshal(value)

    override fun valueToString(value: T?): String =
        when (value) {
            null -> if (nullable) "NULL" else error("Null value for non‑nullable column")
            is Iterable<*> -> notNullValueToDB(value)
            else -> super.valueToString(value)
        }
}
