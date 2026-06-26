package no.nav.emottak.ebms.async

import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.unsafe
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class EbmsInPayloadRecord(
    val offset: Long,
    val key: String,
    val service: String,
    val action: String,
    val cpaId: String,
    val messageId: String,
    val conversationId: String,
    val timestampMs: Long
)

private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

fun HTML.renderEbmsInPayload(
    records: List<EbmsInPayloadRecord>,
    service: String?,
    action: String?,
    cpaId: String?,
    startOffset: Long,
    limit: Int
) {
    head {
        title { +"ebms.in.payload Browser" }
        style {
            unsafe {
                raw(
                    """
                    body { font-family: sans-serif; padding: 1rem; }
                    h1 { font-size: 1.25rem; margin-bottom: 1rem; }
                    .filter-form { display: flex; gap: 0.5rem; flex-wrap: wrap; align-items: flex-end;
                                   padding: 0.75rem; background: #f8f8f8; border: 1px solid #ddd;
                                   border-radius: 4px; margin-bottom: 1rem; }
                    .field { display: flex; flex-direction: column; gap: 0.25rem; }
                    label { font-size: 0.75rem; font-weight: bold; color: #555; }
                    input[type=text], input[type=number] { padding: 0.3rem 0.5rem; border: 1px solid #ccc;
                                                           border-radius: 3px; font-size: 0.875rem; }
                    .btn-primary { padding: 0.4rem 0.8rem; background: #0067c0; color: white; border: none;
                                   border-radius: 3px; cursor: pointer; font-size: 0.875rem; }
                    .btn-primary:hover { background: #0054a3; }
                    table { border-collapse: collapse; width: 100%; font-size: 0.875rem; }
                    th, td { border: 1px solid #ccc; padding: 0.4rem 0.6rem; text-align: left; white-space: nowrap; }
                    th { background: #f0f0f0; font-weight: bold; }
                    tr:nth-child(even) { background: #fafafa; }
                    .retry-btn { padding: 0.25rem 0.6rem; background: #c0392b; color: white; border-radius: 3px;
                                 cursor: pointer; font-size: 0.8rem; text-decoration: none; display: inline-block; }
                    .retry-btn:hover { background: #a93226; }
                    .pagination { margin-top: 1rem; display: flex; gap: 0.5rem; }
                    .page-link { padding: 0.4rem 0.8rem; background: #e0e0e0; border-radius: 3px;
                                 text-decoration: none; color: #333; font-size: 0.875rem; }
                    .page-link:hover { background: #c0c0c0; }
                    .status { color: #666; font-size: 0.875rem; margin-bottom: 0.5rem; }
                    """.trimIndent()
                )
            }
        }
    }
    body {
        h1 { +"ebms.in.payload Browser" }

        form(action = "/api/kafka/ebms-in-payload/view", method = FormMethod.get, classes = "filter-form") {
            div("field") {
                label { htmlFor = "service"; +"Service" }
                input(type = InputType.text, name = "service") {
                    attributes["id"] = "service"
                    value = service ?: ""
                    attributes["placeholder"] = "filter…"
                }
            }
            div("field") {
                label { htmlFor = "action"; +"Action" }
                input(type = InputType.text, name = "action") {
                    attributes["id"] = "action"
                    value = action ?: ""
                    attributes["placeholder"] = "filter…"
                }
            }
            div("field") {
                label { htmlFor = "cpaId"; +"CPA ID" }
                input(type = InputType.text, name = "cpaId") {
                    attributes["id"] = "cpaId"
                    value = cpaId ?: ""
                    attributes["placeholder"] = "filter…"
                }
            }
            div("field") {
                label { htmlFor = "startOffset"; +"Start Offset" }
                input(type = InputType.number, name = "startOffset") {
                    attributes["id"] = "startOffset"
                    value = startOffset.toString()
                    min = "0"
                }
            }
            div("field") {
                label { htmlFor = "limit"; +"Limit (max 500)" }
                input(type = InputType.number, name = "limit") {
                    attributes["id"] = "limit"
                    value = limit.toString()
                    min = "1"
                    max = "500"
                }
            }
            button(type = ButtonType.submit, classes = "btn-primary") { +"Search" }
        }

        p("status") {
            +"Showing ${records.size} messages"
            if (records.isNotEmpty()) +(" (offsets ${records.first().offset}–${records.last().offset})")
        }

        if (records.isEmpty()) {
            p { +"No messages found for the given filters and offset." }
        } else {
            table {
                thead {
                    tr {
                        th { +"Offset" }
                        th { +"Timestamp" }
                        th { +"Key (Request ID)" }
                        th { +"Service" }
                        th { +"Action" }
                        th { +"CPA ID" }
                        th { +"Message ID" }
                        th { +"Conversation ID" }
                        th { +"Retry" }
                    }
                }
                tbody {
                    records.forEach { rec ->
                        tr {
                            td { +rec.offset.toString() }
                            td { +TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(rec.timestampMs)) }
                            td { +rec.key }
                            td { +rec.service }
                            td { +rec.action }
                            td { +rec.cpaId }
                            td { +rec.messageId }
                            td { +rec.conversationId }
                            td {
                                a(href = "/api/retry/incoming/force/offset/${rec.offset}", classes = "retry-btn") {
                                    target = "_blank"
                                    rel = "noopener"
                                    +"Force Retry"
                                }
                            }
                        }
                    }
                }
            }

            div("pagination") {
                if (startOffset > 0) {
                    val prevOffset = (startOffset - limit).coerceAtLeast(0)
                    a(
                        href = "/api/kafka/ebms-in-payload/view?${buildEbmsInPayloadQuery(service, action, cpaId, prevOffset, limit)}",
                        classes = "page-link"
                    ) { +"← Previous" }
                }
                val nextOffset = records.last().offset + 1
                a(
                    href = "/api/kafka/ebms-in-payload/view?${buildEbmsInPayloadQuery(service, action, cpaId, nextOffset, limit)}",
                    classes = "page-link"
                ) { +"Next →" }
            }
        }
    }
}

private fun buildEbmsInPayloadQuery(
    service: String?,
    action: String?,
    cpaId: String?,
    startOffset: Long,
    limit: Int
): String {
    fun encode(v: String) = URLEncoder.encode(v, "UTF-8")
    return buildList {
        if (!service.isNullOrBlank()) add("service=${encode(service)}")
        if (!action.isNullOrBlank()) add("action=${encode(action)}")
        if (!cpaId.isNullOrBlank()) add("cpaId=${encode(cpaId)}")
        add("startOffset=$startOffset")
        add("limit=$limit")
    }.joinToString("&")
}
