package no.nav.emottak.melding.apprec

import no.kith.xmlstds.apprec._2004_11_21.AppRec
import no.kith.xmlstds.apprec._2004_11_21.AppRec.Receiver
import no.kith.xmlstds.apprec._2004_11_21.CS
import no.kith.xmlstds.apprec._2004_11_21.CV
import no.kith.xmlstds.apprec._2004_11_21.HCP
import no.kith.xmlstds.apprec._2004_11_21.HCPerson
import no.kith.xmlstds.apprec._2004_11_21.ObjectFactory
import no.kith.xmlstds.msghead._2006_05_24.HealthcareProfessional
import no.kith.xmlstds.msghead._2006_05_24.Ident
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.kith.xmlstds.msghead._2006_05_24.MsgInfo
import no.nav.emottak.util.toXMLGregorianCalendar
import org.apache.commons.lang3.StringUtils
import java.time.Instant
import java.util.UUID

private val objectFactory = ObjectFactory()
private const val OID_8221 = "2.16.578.1.12.4.1.1.8221"
private const val VERSION_1_0 = "1.0 2004-11-21"

private const val APPREC = "APPREC"
private const val APPLIKASJONSKVITTERING = "Applikasjonskvittering"

private const val NAV = "NAV"
private const val NAV_HERID = "79768"
private const val NAV_ORGNO = "889640782"

private const val DEPT_NAME = "Elektronisk mottak"

private const val OK_VALUE = "1"
private const val OK_DESCRIPTION = "OK"
private const val NOK_VALUE = "2"
private const val NOK_DESCRIPTION = "Avvist"

fun createPositiveApprec(msgHead: MsgHead): AppRec = createBaseApprec(msgHead).also {
    it.status = createCS(OK_VALUE, OK_DESCRIPTION)
}

fun createNegativeApprec(msgHead: MsgHead, exception: Exception): AppRec = createBaseApprec(msgHead).also {
    it.status = createCS(NOK_VALUE, NOK_DESCRIPTION)
    it.error.add(createApprecError(AppRecErrorCode.getCode(exception), exception.message))
}

private fun createBaseApprec(msgHead: MsgHead): AppRec = objectFactory.createAppRec().also { apprec ->
    apprec.genDate = Instant.now().toXMLGregorianCalendar()
    apprec.id = UUID.randomUUID().toString()
    apprec.miGversion = VERSION_1_0
    apprec.msgType = createCS(APPREC,APPLIKASJONSKVITTERING)
    apprec.originalMsgId = createOriginalMessageId(msgHead.msgInfo)
    apprec.receiver = createReceiver(msgHead.msgInfo)
    apprec.sender = createSender()
}

private fun createApprecError(errorCode: AppRecErrorCode, detaljertFeil: String? = null): CV = objectFactory.createCV().also {
    it.s = OID_8221
    it.v = errorCode.name
    it.dn = errorCode.description
    it.ot = detaljertFeil
}

private fun createReceiver(msgInfo: MsgInfo): Receiver = objectFactory.createAppRecReceiver().also {
    val org = msgInfo.sender.organisation
    val name = org.organisationName
    val ident = org.ident.getPreferredIdent()
    it.hcp = createHealthCareProfessional(ident.id, name, OrgUnit.valueOf(ident.typeId.v), org.healthcareProfessional)
}

private fun createHealthCareProfessional(id: String, name: String, orgUnit: OrgUnit, healthcareProfessional: HealthcareProfessional? = null): HCP =
    objectFactory.createHCP().also {
        it.inst = objectFactory.createInst().also { inst ->
            inst.id = id
            inst.name = name
            inst.typeId = createCS(orgUnit.name, orgUnit.description)
            if (healthcareProfessional != null) {
                inst.hcPerson.add(createHCPerson(healthcareProfessional))
            }
        }
    }

private fun createHCPerson(healthcareProfessional: HealthcareProfessional): HCPerson =
    objectFactory.createHCPerson().also {
        val ident: Ident = healthcareProfessional.ident.getPreferredIdent()
        it.id = ident.id
        it.typeId = createCS(ident.typeId.v)
        it.name = StringUtils.normalizeSpace(
            StringUtils.join(listOf(
                healthcareProfessional.givenName,
                healthcareProfessional.middleName,
                healthcareProfessional.familyName
            ), ' ')
        )
    }

private fun createSender(): AppRec.Sender =
    objectFactory.createAppRecSender().also { sender ->
        sender.hcp = createHealthCareProfessional(NAV_ORGNO, NAV, OrgUnit.ENH).also { hcp ->
            hcp.inst.dept.add(objectFactory.createDept().also { it.name = DEPT_NAME })
        }
    }

private fun createOriginalMessageId(msgInfo: MsgInfo) = objectFactory.createOriginalMsgId().also {
    it.id = msgInfo.msgId
    it.issueDate = msgInfo.genDate
    it.msgType = createCS(msgInfo.type.v, msgInfo.type.dn)
}

private fun createCS(v: String): CS = objectFactory.createCS().also { it.v = v }
private fun createCS(v: String, dn: String): CS = createCS(v).also { it.dn = dn }


private fun List<Ident>.getPreferredIdent(): Ident =
    this.firstOrNull { it.typeId.v == OrgUnit.ENH.name }
        ?: this.firstOrNull { it.typeId.v == OrgUnit.HER.name }
        ?: this.firstOrNull() ?: throw RuntimeException("Ident liste tom, dette skal ikke skje!")

private enum class OrgUnit(val description: String) {
    ENH("Organisasjonsnummeret i Enhetsregisteret"),
    HER("Identifikator fra Helsetjenesteenhetsregisteret (HER-id)"),
    LIN("Lokal identifikator for institusjoner");
}
