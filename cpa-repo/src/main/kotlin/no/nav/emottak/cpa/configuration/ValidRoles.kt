package no.nav.emottak.cpa.configuration

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceSource
import no.nav.emottak.message.model.Direction

data class RoleMapping(
    val direction: Direction,
    val service: String,
    val action: String,
    val fromRole: String,
    val toRole: String
)

data class ValidRolesConfig(
    val validRoles: List<RoleMapping>
)

class ValidRoles(private val mappings: List<RoleMapping>) {
    private val outgoing: Map<Pair<String, String>, RoleMapping> =
        mappings.filter { it.direction == Direction.OUT }
            .groupBy { it.service to it.action }
            .mapValues { (key, entries) ->
                require(entries.size == 1) { "Duplicate outgoing role mapping for service '${key.first}', action '${key.second}'" }
                entries.single()
            }

    fun findOutgoing(service: String, action: String): RoleMapping? = outgoing[service to action]

    fun isValidIncoming(fromRole: String, toRole: String, service: String, action: String): Boolean =
        mappings.any {
            it.direction == Direction.IN && it.service == service && it.action == action &&
                it.fromRole == fromRole && it.toRole == toRole
        }

    companion object {
        const val RESOURCE = "/roles/valid-roles.yaml"

        fun load(resource: String = RESOURCE): ValidRoles = ValidRoles(
            ConfigLoaderBuilder.default()
                .addResourceSource(resource)
                .build()
                .loadConfigOrThrow<ValidRolesConfig>()
                .validRoles
        )
    }
}

val validRoles by lazy { ValidRoles.load() }
