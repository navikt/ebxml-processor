package no.nav.emottak.ebms.async

import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.unsafe
import no.nav.emottak.ebms.async.persistence.repository.MessagePendingAckSummary

fun HTML.renderMessagesPendingAck(messages: List<MessagePendingAckSummary>) {
    head {
        title { +"Messages Pending Ack" }
        style {
            unsafe {
                raw(
                    """
                    body { font-family: sans-serif; padding: 1rem; }
                    h1 { font-size: 1.25rem; margin-bottom: 1rem; }
                    table { border-collapse: collapse; width: 100%; font-size: 0.875rem; }
                    th, td { border: 1px solid #ccc; padding: 0.4rem 0.6rem; text-align: left; }
                    th { background: #f0f0f0; font-weight: bold; }
                    tr:nth-child(even) { background: #fafafa; }
                    """.trimIndent()
                )
            }
        }
    }
    body {
        h1 { +"Messages Pending Ack (${messages.size})" }
        table {
            thead {
                tr {
                    th { +"Request ID" }
                    th { +"Conversation ID" }
                    th { +"Message ID" }
                    th { +"Ref To Message ID" }
                    th { +"CPA ID" }
                    th { +"Service" }
                    th { +"Action" }
                    th { +"Receiver HER ID" }
                    th { +"Receiver Orgnummer" }
                    th { +"Email List" }
                    th { +"First Sent" }
                    th { +"Last Sent" }
                    th { +"Resent Count" }
                }
            }
            tbody {
                messages.forEach { msg ->
                    tr {
                        td { +msg.requestId }
                        td { +msg.conversationId }
                        td { +msg.messageId }
                        td { +(msg.refToMessageId ?: "") }
                        td { +msg.cpaId }
                        td { +msg.service }
                        td { +msg.action }
                        td { +(msg.receiverHerId ?: "") }
                        td { +(msg.receiverOrgnummer ?: "") }
                        td { +msg.emailAddressList.joinToString(", ") }
                        td { +msg.firstSent }
                        td { +msg.lastSent }
                        td { +msg.resentCount.toString() }
                    }
                }
            }
        }
    }
}
