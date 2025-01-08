package no.nav.emottak.ebms.persistence

import no.nav.emottak.ebms.persistence.EbmsMessageTable.action
import no.nav.emottak.ebms.persistence.EbmsMessageTable.conversationId
import no.nav.emottak.ebms.persistence.EbmsMessageTable.cpaId
import no.nav.emottak.ebms.persistence.EbmsMessageTable.fromPartyId
import no.nav.emottak.ebms.persistence.EbmsMessageTable.fromRole
import no.nav.emottak.ebms.persistence.EbmsMessageTable.messageId
import no.nav.emottak.ebms.persistence.EbmsMessageTable.refToMessageId
import no.nav.emottak.ebms.persistence.EbmsMessageTable.service
import no.nav.emottak.ebms.persistence.EbmsMessageTable.toPartyId
import no.nav.emottak.ebms.persistence.EbmsMessageTable.toRole
import no.nav.emottak.message.model.EbmsMessage
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

class EbmsMessageRepository(private val database: Database) {

    val unsupportedServices = listOf(
        "HarBorgerFrikort",
        "HarBorgerEgenandelFritak"
    )

    fun updateOrInsert(dto: EbmsMessageDto): String {
        transaction(database.db) {
            EbmsMessageTable.upsert(EbmsMessageTable.messageId, EbmsMessageTable.cpaId) {
                it[cpaId] = dto.cpaId
                it[conversationId] = dto.conversationId
                it[messageId] = dto.messageId
                it[refToMessageId] = dto.refToMessageId
                it[fromPartyId] = dto.fromPartyId
                it[fromRole] = dto.fromRole
                it[toPartyId] = dto.toPartyId
                it[toRole] = dto.toRole
                it[service] = dto.service
                it[action] = dto.action
            }
        }
        return dto.messageId
    }

    fun getByMessageIdAndCpaId(messageId: String, cpaId: String): EbmsMessageDto? {
        var dto: EbmsMessageDto? = null

        transaction(database.db) {
            EbmsMessageTable
                .select(EbmsMessageTable.columns)
                .where { (EbmsMessageTable.messageId.eq(messageId)) and (EbmsMessageTable.cpaId.eq(cpaId)) }
                .firstOrNull()
                ?.also {
                    dto = EbmsMessageDto(
                        it[EbmsMessageTable.cpaId],
                        it[EbmsMessageTable.conversationId],
                        it[EbmsMessageTable.messageId],
                        it[EbmsMessageTable.refToMessageId],
                        it[EbmsMessageTable.fromPartyId],
                        it[EbmsMessageTable.fromRole],
                        it[EbmsMessageTable.toPartyId],
                        it[EbmsMessageTable.toRole],
                        it[EbmsMessageTable.service],
                        it[EbmsMessageTable.action]
                    )
                }
        }
        return dto
    }

    fun saveEbmsMessageDetails(message: EbmsMessage): String {
        if (!unsupportedServices.contains(message.addressing.service)) {
            val dto = EbmsMessageDto(
                message.cpaId,
                message.conversationId,
                message.messageId,
                message.refToMessageId,
                message.addressing.from.partyId[0].value,
                message.addressing.from.role,
                message.addressing.to.partyId[0].value,
                message.addressing.to.role,
                message.addressing.service,
                message.addressing.action
            )
            return updateOrInsert(dto)
        }
        return ""
    }

    data class EbmsMessageDto(
        val cpaId: String,
        val conversationId: String,
        val messageId: String,
        val refToMessageId: String?,
        val fromPartyId: String,
        val fromRole: String?,
        val toPartyId: String,
        val toRole: String?,
        val service: String,
        val action: String
    )
}
