package no.nav.emottak.cpa.configuration

import io.ktor.server.plugins.NotFoundException
import no.nav.emottak.cpa.feil.CpaValidationException
import no.nav.emottak.cpa.resolveOutgoingAddressing
import no.nav.emottak.cpa.validateIncomingRoles
import no.nav.emottak.message.ebxml.EbXMLConstants.ACKNOWLEDGMENT_ACTION
import no.nav.emottak.message.ebxml.EbXMLConstants.EBMS_SERVICE_URI
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ValidRolesTest {

    private val roles = ValidRoles.load()

    @Test
    fun `every role mapping has a matching process config for the sending role`() {
        val sql = javaClass.getResource("/db/migration/R__init_process_config.sql")!!.readText()
        val processConfigKeys = Regex("""\(\s*'([^']+)'\s*,\s*'([^']+)'\s*,\s*'([^']+)'""")
            .findAll(sql)
            .map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            .toSet()

        val config = javaClass.getResource(ValidRoles.RESOURCE)!!.readText()
        val mappings = Regex("""direction:\s*(\w+),\s*service:\s*(\w+),\s*action:\s*(\w+),\s*fromRole:\s*(\w+),\s*toRole:\s*(\w+)""")
            .findAll(config)
            .map { m -> m.groupValues.drop(1).let { RoleMapping(Direction.valueOf(it[0]), it[1], it[2], it[3], it[4]) } }
            .toList()

        assert(mappings.isNotEmpty())
        mappings.forEach { m ->
            when (m.direction) {
                Direction.OUT -> assertEquals(m, roles.findOutgoing(m.service, m.action))
                Direction.IN -> assert(roles.isValidIncoming(m.fromRole, m.toRole, m.service, m.action))
            }
            assert(Triple(m.fromRole, m.service, m.action) in processConfigKeys) {
                "Mangler process_config for (${m.fromRole}, ${m.service}, ${m.action})"
            }
        }
    }

    @Test
    fun `duplicate outgoing service and action is rejected`() {
        assertThrows<IllegalArgumentException> {
            ValidRoles(
                listOf(
                    RoleMapping(Direction.OUT, "S", "A", "R1", "R2"),
                    RoleMapping(Direction.OUT, "S", "A", "R3", "R4")
                )
            )
        }
    }

    @Test
    fun `same service and action in both directions resolves outgoing roles`() {
        val resolved = request(Direction.OUT, "DialogmoteInnkalling", "MoteRespons").resolveOutgoingAddressing(roles)
        assertEquals("Saksbehandler", resolved.from.role)
        assertEquals("Sykmelder", resolved.to.role)
        assertEquals("8141253", resolved.from.partyId.single().value)
        assertEquals("123", resolved.to.partyId.single().value)
    }

    @Test
    fun `unknown outgoing service and action fails`() {
        assertThrows<NotFoundException> {
            request(Direction.OUT, "Sykmelding", "Ukjent").resolveOutgoingAddressing(roles)
        }
    }

    @Test
    fun `valid incoming combination passes`() {
        assertDoesNotThrow {
            request(Direction.IN, "DialogmoteInnkalling", "MoteRespons", "Sykmelder", "Saksbehandler").validateIncomingRoles(roles)
        }
    }

    @Test
    fun `incoming combination only valid for outgoing is rejected`() {
        assertThrows<CpaValidationException> {
            request(Direction.IN, "DialogmoteInnkalling", "MoteRespons", "Saksbehandler", "Sykmelder").validateIncomingRoles(roles)
        }
    }

    @Test
    fun `incoming with wrong role is rejected`() {
        assertThrows<CpaValidationException> {
            request(Direction.IN, "Sykmelding", "Registrering", "Lege", "Saksbehandler").validateIncomingRoles(roles)
        }
    }

    @Test
    fun `ebms signal messages are not checked`() {
        val out = request(Direction.OUT, EBMS_SERVICE_URI, ACKNOWLEDGMENT_ACTION)
        assertSame(out.addressing, out.resolveOutgoingAddressing(roles))
        assertDoesNotThrow { request(Direction.IN, EBMS_SERVICE_URI, ACKNOWLEDGMENT_ACTION).validateIncomingRoles(roles) }
    }

    private fun request(
        direction: Direction,
        service: String,
        action: String,
        fromRole: String = "Ukjent",
        toRole: String = "Ukjent"
    ) = ValidationRequest(
        direction = direction,
        messageId = "messageId",
        conversationId = "conversationId",
        cpaId = "nav:qass:12345",
        addressing = Addressing(
            to = Party(listOf(PartyId("HER", "123")), toRole),
            from = Party(listOf(PartyId("HER", "8141253")), fromRole),
            service = service,
            action = action
        )
    )
}
