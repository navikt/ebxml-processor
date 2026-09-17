package no.nav.emottak.message.ebxml

import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader

fun MessageHeader.addressing(isRoleApplicable: Boolean = true) = Addressing(
    Party(
        this.to.partyId.map { PartyId(it.type!!, it.value!!) },
        if (isRoleApplicable) this.to.role!! else "Not applicable"
    ),
    Party(
        this.from.partyId.map { PartyId(it.type!!, it.value!!) },
        if (isRoleApplicable) this.from.role!! else "Not applicable"
    ),
    this.service.value!!,
    this.action
)
