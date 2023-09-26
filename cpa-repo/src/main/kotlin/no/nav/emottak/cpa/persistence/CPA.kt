package no.nav.emottak.cpa.persistence

import no.nav.emottak.cpa.marshal
import no.nav.emottak.cpa.unmarshal
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement

class CPA : Table("cpa") {
    val id: Column<String> = varchar("id",256)
    val cpa = json("cpa", CollaborationProtocolAgreement::class.java)
}


private fun <T : Any> Table.json(
    name: String,
    clazz: Class<T>
): Column<T> =
    registerColumn(
        name = name,
        type = JsonColumnType(clazz)
    )


class JsonColumnType<T : Any>(private val clazz: Class<T>) : ColumnType() {
    override fun sqlType(): String =
        "json"

    override fun valueFromDB(value: Any): T =   unmarshal(value as String, clazz)

    override fun notNullValueToDB(value: Any): String = marshal(value)

    override fun valueToString(value: Any?): String =
        when (value) {
            is Iterable<*> -> notNullValueToDB(value)
            else -> super.valueToString(value)
        }
}

