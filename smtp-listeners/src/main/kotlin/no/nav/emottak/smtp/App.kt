package no.nav.emottak.smtp

import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

fun main() {
    // if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
    DecoroutinatorRuntime.load()
    // }
    embeddedServer(Netty, port = 8080, module = Application::myApplicationModule).start(wait = true)
}

internal val log = LoggerFactory.getLogger("no.nav.emottak.smtp")

fun Application.myApplicationModule() {
    install(ContentNegotiation) {
        json()
    }
    routing {
        if (getEnvVar("NAIS_CLUSTER_NAME") != "prod-fss") {
            mailCheck()
            mailRead()
            logOutgoing()
            testAzureAuthToCpaRepo()
        }
        registerHealthEndpoints()
        cpaSync()
    }
}
