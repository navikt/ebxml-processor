package no.nav.emottak.ebms.persistence.repository

import no.nav.emottak.ebms.persistence.Database
import no.nav.emottak.ebms.persistence.table.EventsTable
import no.nav.emottak.ebms.persistence.table.EventsTable.contentId
import no.nav.emottak.ebms.persistence.table.EventsTable.createdAt
import no.nav.emottak.ebms.persistence.table.EventsTable.eventId
import no.nav.emottak.ebms.persistence.table.EventsTable.eventMessage
import no.nav.emottak.ebms.persistence.table.EventsTable.juridiskLoggId
import no.nav.emottak.ebms.persistence.table.EventsTable.messageId
import no.nav.emottak.ebms.persistence.table.EventsTable.referenceId
import no.nav.emottak.message.model.Event
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class EventsRepository(private val database: Database) {

    fun updateOrInsert(event: Event): Uuid {
        transaction(database.db) {
            EventsTable.upsert(eventId) {
                it[eventId] = event.eventId.toJavaUuid()
                it[referenceId] = event.referenceId.toJavaUuid()
                it[contentId] = event.contentId
                it[messageId] = event.messageId
                it[juridiskLoggId] = event.juridiskLoggId
                it[eventMessage] = event.eventMessage
                if (event.createdAt != null) {
                    it[createdAt] = event.createdAt!!
                }
            }
        }
        return event.eventId
    }

    fun getByEventId(eventId: Uuid): Event? {
        var event: Event? = null

        transaction(database.db) {
            EventsTable
                .select(EventsTable.columns)
                .where { EventsTable.eventId.eq(eventId.toJavaUuid()) }
                .firstOrNull()
                ?.also {
                    event = Event(
                        it[EventsTable.eventId].toKotlinUuid(),
                        it[referenceId].toKotlinUuid(),
                        it[contentId],
                        it[messageId],
                        it[juridiskLoggId],
                        it[eventMessage],
                        it[createdAt]
                    )
                }
        }
        return event
    }
}
