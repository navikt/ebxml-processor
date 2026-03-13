package no.nav.emottak.ebms.async.util

import no.nav.emottak.utils.common.model.PartyId

fun List<PartyId>.getPreferredPartyId(): PartyId = this.firstOrNull { it.type == "HER" }
    ?: this.firstOrNull { it.type == "orgnummer" }
    ?: this.firstOrNull { it.type == "ENH" }
    ?: this.first()
