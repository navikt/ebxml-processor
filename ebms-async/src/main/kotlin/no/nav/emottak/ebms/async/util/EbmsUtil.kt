package no.nav.emottak.ebms.async.util

import no.nav.emottak.utils.common.model.PartyId
import kotlin.time.Duration

fun List<PartyId>.getPreferredPartyId(): PartyId = this.firstOrNull { it.type == "HER" }
    ?: this.firstOrNull { it.type == "orgnummer" }
    ?: this.firstOrNull { it.type == "ENH" }
    ?: this.first()

internal fun Duration.readableInterval(): String {
    this.toComponents { days, hours, minutes, seconds, nanoseconds ->
        var readable = ""
        if (days > 0) readable = "$days days"
        if (hours > 0) readable = if (readable != "") "$readable, $hours hours" else "$hours hours"
        if (minutes > 0) readable = if (readable != "") "$readable, $minutes minutes" else "$minutes minutes"
        return readable
    }
}
