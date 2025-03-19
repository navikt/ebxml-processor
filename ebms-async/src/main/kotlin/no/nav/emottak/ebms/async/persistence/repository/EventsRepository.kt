package no.nav.emottak.ebms.async.persistence.repository

import no.nav.emottak.ebms.async.persistence.Database
import no.nav.emottak.ebms.async.persistence.table.EventsTable
import no.nav.emottak.ebms.async.persistence.table.EventsTable.contentId
import no.nav.emottak.ebms.async.persistence.table.EventsTable.createdAt
import no.nav.emottak.ebms.async.persistence.table.EventsTable.eventId
import no.nav.emottak.ebms.async.persistence.table.EventsTable.eventMessage
import no.nav.emottak.ebms.async.persistence.table.EventsTable.juridiskLoggId
import no.nav.emottak.ebms.async.persistence.table.EventsTable.messageId
import no.nav.emottak.ebms.async.persistence.table.EventsTable.requestId
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.Event
import no.nav.emottak.message.model.log
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
                it[requestId] = event.requestId.toJavaUuid()
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
                        it[requestId].toKotlinUuid(),
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

@OptIn(ExperimentalUuidApi::class)
fun EbmsMessage.saveEvent(message: String, eventsRepository: EventsRepository) {
    log.info(this.marker(), message)
    eventsRepository.updateOrInsert(
        Event(
            eventId = Uuid.random(),
            requestId = Uuid.parse(this.requestId),
            messageId = this.messageId,
            eventMessage = message
        )
    )
}
