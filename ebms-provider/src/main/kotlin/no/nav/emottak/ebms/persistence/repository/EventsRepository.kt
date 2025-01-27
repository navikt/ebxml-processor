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
import java.util.UUID

class EventsRepository(private val database: Database) {

    fun updateOrInsert(event: Event): UUID {
        transaction(database.db) {
            EventsTable.upsert(eventId) {
                it[eventId] = event.eventId
                it[referenceId] = event.referenceId
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

    fun getByEventId(eventId: UUID): Event? {
        var event: Event? = null

        transaction(database.db) {
            EventsTable
                .select(EventsTable.columns)
                .where { EventsTable.eventId.eq(eventId) }
                .firstOrNull()
                ?.also {
                    event = Event(
                        it[EventsTable.eventId],
                        it[referenceId],
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
