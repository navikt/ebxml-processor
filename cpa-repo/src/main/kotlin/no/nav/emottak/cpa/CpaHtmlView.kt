package no.nav.emottak.cpa

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.head
import kotlinx.html.input
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import no.nav.emottak.util.createX509Certificate
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.DeliveryChannel
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PartyInfo
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.StatusValueType
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.Transport
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault())

fun HTML.renderCpa(cpa: CollaborationProtocolAgreement) {
    val isActive = cpa.status.value == StatusValueType.AGREED
    head {
        title { +"CPA: ${cpa.cpaid}" }
        style { +CPA_STYLES }
    }
    body {
        div("container") {
            div("header") {
                h1 { +"Collaboration Protocol Agreement" }
                div("header-meta") {
                    form(classes = "cpa-search") {
                        attributes["onsubmit"] = "event.preventDefault(); window.location='/cpa/'+document.getElementById('cpaIdInput').value+'/view'"
                        input(type = InputType.text, name = "cpaId") {
                            attributes["id"] = "cpaIdInput"
                            placeholder = "Enter CPA ID…"
                            value = cpa.cpaid
                        }
                        input(type = InputType.submit) { value = "Go" }
                    }
                    span("badge ${if (isActive) "badge-active" else "badge-inactive"}") {
                        +(cpa.status?.value?.value() ?: "unknown")
                    }
                    span("version") { +"v${cpa.version}" }
                    a(href = "/cpa/${cpa.cpaid}", classes = "raw-link") { +"Raw XML" }
                }
            }

            div("card") {
                h2 { +"Summary" }
                div("grid-3") {
                    labeledValue("CPA ID", cpa.cpaid)
                    labeledValue("Valid from", dateFormatter.format(cpa.start.toInstant()))
                    labeledValue(
                        "Valid to",
                        dateFormatter.format(cpa.end.toInstant()),
                        expired = cpa.end.toInstant() < java.time.Instant.now()
                    )
                }
            }

            h2 { +"Parties" }

            // Party names + identifiers
            div("party-row") {
                cpa.partyInfo.forEach { party ->
                    div("card") {
                        h3 { +(party.partyName ?: "Unnamed party") }
                        if (party.partyId.isNotEmpty()) {
                            div("section") {
                                h4 { +"Identifiers" }
                                table("table") {
                                    thead { tr { th { +"Type" }; th { +"Value" } } }
                                    tbody {
                                        party.partyId.forEach { pid ->
                                            tr {
                                                td { +(pid.type ?: "-") }
                                                td { +(pid.value ?: "-") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Certificates
            div("party-row") {
                cpa.partyInfo.forEach { party -> div("card") { renderCertificates(party) } }
            }

            // Collaboration Roles
            div("party-row") {
                cpa.partyInfo.forEach { party ->
                    div("card") {
                        if (party.collaborationRole.isNotEmpty()) {
                            h4 { +"Collaboration Roles" }
                            party.collaborationRole.forEach { role ->
                                div("role-card") {
                                    div("role-header") {
                                        span("role-name") { +(role.role?.name ?: "Unknown") }
                                        span("process-name") {
                                            +"${role.processSpecification?.name ?: "-"} ${role.processSpecification?.version ?: ""}".trim()
                                        }
                                    }
                                    val sb = role.serviceBinding
                                    if (sb != null) {
                                        div("service-label") { +"Service: ${sb.service?.value ?: "-"}" }
                                        table("table") {
                                            thead {
                                                tr {
                                                    th { +"Direction" }
                                                    th { +"Action" }
                                                    th { +"Channel" }
                                                }
                                            }
                                            tbody {
                                                sb.canSend.forEach { cs ->
                                                    val binding = cs.thisPartyActionBinding
                                                    val channelId = (binding?.channelId?.firstOrNull()?.value as? DeliveryChannel)?.channelId
                                                    tr {
                                                        td { span("action-tag send") { +"↑ Send" } }
                                                        td { +(binding?.action ?: "-") }
                                                        td("monospace") { +(channelId ?: "-") }
                                                    }
                                                }
                                                sb.canReceive.forEach { cr ->
                                                    val binding = cr.thisPartyActionBinding
                                                    val channelId = (binding?.channelId?.firstOrNull()?.value as? DeliveryChannel)?.channelId
                                                    tr {
                                                        td { span("action-tag receive") { +"↓ Receive" } }
                                                        td { +(binding?.action ?: "-") }
                                                        td("monospace") { +(channelId ?: "-") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Delivery Channels
            div("party-row") {
                cpa.partyInfo.forEach { party ->
                    div("card") {
                        if (party.deliveryChannel.isNotEmpty()) {
                            h4 { +"Delivery Channels" }
                            party.deliveryChannel.forEach { dc ->
                                val transport = dc.transportId as? Transport
                                val endpoints = transport?.transportReceiver?.endpoint.orEmpty()
                                div("channel-card") {
                                    div("channel-header") {
                                        strong { +dc.channelId }
                                        if (endpoints.isNotEmpty()) {
                                            span("endpoint-uri") { +endpoints.first().uri }
                                        }
                                    }
                                    val mc = dc.messagingCharacteristics
                                    if (mc != null) {
                                        div("grid-2 small-grid") {
                                            labeledValue("Sync reply", mc.syncReplyMode?.value() ?: "-")
                                            labeledValue("Ack requested", mc.ackRequested?.value() ?: "-")
                                            labeledValue("Ack signature", mc.ackSignatureRequested?.value() ?: "-")
                                            labeledValue("Duplicate elimination", mc.duplicateElimination?.value() ?: "-")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.labeledValue(label: String, value: String, expired: Boolean = false) {
    div("labeled-value") {
        span("label") { +label }
        span(if (expired) "value value-expired" else "value") { +value }
    }
}

private fun FlowContent.renderCertificates(party: PartyInfo) {
    if (party.certificate.isEmpty()) return
    h4 { +"Certificates" }
    party.certificate.forEach { cert ->
        runCatching { createX509Certificate(cert.getX509Certificate()) }
            .onSuccess { x509 ->
                div("cert-card") {
                    val now = System.currentTimeMillis()
                    val expired = x509.notAfter.time < now
                    val notYetValid = x509.notBefore.time > now
                    div("cert-header") {
                        span("cert-id") { +cert.certId }
                        if (expired) {
                            span("badge badge-inactive") { +"Expired" }
                        } else if (notYetValid) {
                            span("badge badge-inactive") { +"Not yet valid" }
                        } else {
                            span("badge badge-active") { +"Valid" }
                        }
                    }
                    div("cert-grid") {
                        labeledValue("Subject DN", x509.subjectX500Principal.name)
                        labeledValue("Issuer DN", x509.issuerX500Principal.name)
                        labeledValue("Serial number", x509.serialNumber.toString(16).uppercase())
                        labeledValue("Thumbprint (SHA-1)", thumbprint(x509))
                        labeledValue("Valid from", dateFormatter.format(x509.notBefore.toInstant()))
                        labeledValue(
                            "Valid to",
                            dateFormatter.format(x509.notAfter.toInstant()),
                            expired = expired
                        )
                    }
                }
            }
    }
}

private fun thumbprint(cert: X509Certificate): String =
    MessageDigest.getInstance("SHA-1").digest(cert.encoded)
        .joinToString(":") { "%02X".format(it) }

private val CPA_STYLES = """
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
           background: #f5f6fa; color: #1a1a2e; font-size: 14px; }
    .container { max-width: 1200px; margin: 0 auto; padding: 24px; }

    .header { display: flex; align-items: center; justify-content: space-between;
              margin-bottom: 24px; padding-bottom: 16px; border-bottom: 2px solid #e0e0f0; }
    .header h1 { font-size: 22px; font-weight: 700; color: #1a1a2e; }
    .header-meta { display: flex; align-items: center; gap: 12px; }
    .cpa-search { display: flex; gap: 4px; }
    .cpa-search input[type=text] { font-size: 13px; padding: 4px 8px; border: 1px solid #d0d0e8;
                                   border-radius: 4px; width: 220px; outline: none; }
    .cpa-search input[type=text]:focus { border-color: #6366f1; }
    .cpa-search input[type=submit] { font-size: 13px; padding: 4px 10px; background: #6366f1; color: #fff;
                                     border: none; border-radius: 4px; cursor: pointer; }
    .cpa-search input[type=submit]:hover { background: #4f46e5; }
    .version { font-size: 12px; color: #666; background: #f0f0f8; padding: 4px 8px; border-radius: 4px; }
    .raw-link { font-size: 12px; color: #1d4ed8; text-decoration: none; border: 1px solid #bfdbfe;
                padding: 4px 10px; border-radius: 4px; }
    .raw-link:hover { background: #eff6ff; }

    .badge { padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 600; text-transform: uppercase; }
    .badge-active { background: #d4f5e2; color: #1a7a40; }
    .badge-inactive { background: #fde8e8; color: #c0392b; }

    h2 { font-size: 16px; font-weight: 600; color: #333; margin: 20px 0 12px; }
    h3 { font-size: 15px; font-weight: 600; margin-bottom: 16px; color: #1a1a2e; }
    h4 { font-size: 13px; font-weight: 600; color: #555; margin-bottom: 8px; text-transform: uppercase;
         letter-spacing: 0.5px; }

    .card { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 16px;
            box-shadow: 0 1px 4px rgba(0,0,0,0.08); }

    .party-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
    .party-row > .card { margin-bottom: 0; }

    .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
    .grid-3 { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; }
    .small-grid { margin-top: 8px; }
    .labeled-value { display: flex; flex-direction: column; }
    .labeled-value .label { font-size: 11px; color: #888; text-transform: uppercase; letter-spacing: 0.4px; }
    .labeled-value .value { font-size: 13px; color: #1a1a2e; font-weight: 500; margin-top: 2px; }
    .labeled-value .value-expired { color: #b91c1c; font-weight: 700; }

    .table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .table th { text-align: left; padding: 6px 10px; background: #f8f8fc;
                border-bottom: 1px solid #e8e8f0; font-weight: 600; color: #555; }
    .table td { padding: 6px 10px; border-bottom: 1px solid #f0f0f8; }
    .table tr:last-child td { border-bottom: none; }

    .role-card { background: #f8f8fc; border-radius: 6px; padding: 12px; margin-bottom: 8px; }
    .role-header { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
    .role-name { font-weight: 600; font-size: 13px; }
    .process-name { font-size: 12px; color: #666; background: #ebebf8; padding: 2px 6px; border-radius: 4px; }
    .service-label { font-size: 12px; color: #555; margin-bottom: 6px; }
    .action-tag { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 500; white-space: nowrap; }
    .action-tag.send { background: #dbeafe; color: #1d4ed8; }
    .action-tag.receive { background: #dcfce7; color: #15803d; }
    .monospace { font-family: monospace; font-size: 12px; color: #555; }

    .channel-card { background: #f8f8fc; border-radius: 6px; padding: 10px 12px; margin-bottom: 8px; }
    .channel-header { display: flex; align-items: baseline; gap: 10px; margin-bottom: 6px; flex-wrap: wrap; }
    .channel-header strong { font-size: 13px; }

    .cert-card { background: #f8f8fc; border-radius: 6px; padding: 12px; margin-bottom: 8px; }
    .cert-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
    .cert-id { font-family: monospace; font-size: 12px; color: #444; flex: 1; }
    .cert-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
    .cert-grid .labeled-value .value { font-size: 12px; word-break: break-all; }

    .endpoint-uri { font-family: monospace; font-size: 12px; color: #555; word-break: break-all; }
""".trimIndent()
