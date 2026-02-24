package no.nav.emottak.ebms.async

import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.KafkaTestContainer
import no.nav.emottak.ebms.async.persistence.Database
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.testcontainers.containers.PostgreSQLContainer
import java.io.File

const val START_MOCK_OAUTH = true
const val MOCK_OAUTH_PORT = 3344

// store config in local files to avoid needing to copy URLs etc from this process' log to clients
// EDIT THIS to match some directory on your local machine
fun localTmp(subpath: String): String {
    if (File("/tmp").isDirectory()) return "/tmp" + subpath
    if (File("c:/tmp").isDirectory()) return "c:/tmp" + subpath
    throw RuntimeException("Could not find a suitable directory to store config in, fix the code and restart this process !!")
}

const val START_KAFKA = true
const val KAFKA_BROKERS_STORAGE = "/kafka_brokers.txt"

const val START_POSTGRES = true
const val POSTGRES_TEST_USER = "emottak-ebms-db-admin"
const val POSTGRES_TEST_PW = "test"
const val POSTGRES_JDBCURL_STORAGE = "/postgres_jdbcurl.txt"

fun main() {
    var mockOAuth2Server: MockOAuth2Server? = null
    if (START_MOCK_OAUTH) {
        println("=== Starting MOCK Auth server ==")
        mockOAuth2Server = MockOAuth2Server().also { it.start(port = MOCK_OAUTH_PORT) }
    }

    var ebmsProviderDbContainer: PostgreSQLContainer<Nothing>? = null
    if (START_POSTGRES) {
        println("=== Starting Postgres ==")
        // Make flyway find the migration scripts, see Database.kt
        System.setProperty("NAIS_CLUSTER_NAME", "test")
        ebmsProviderDbContainer = ebmsPostgres()
        ebmsProviderDbContainer.start()
        val database = Database(ebmsProviderDbContainer.testConfiguration())

        println("=== Creating DB tables ==")
        database.migrate(database.dataSource)

        File(localTmp(POSTGRES_JDBCURL_STORAGE)).writeText(ebmsProviderDbContainer.jdbcUrl)
    }

    if (START_KAFKA) {
        println("=== Starting Kafka ==")
        KafkaTestContainer.start()

        println("=== Creating topics ==")
        System.setProperty("KAFKA_BROKERS", KafkaTestContainer.bootstrapServers)
        KafkaTestContainer.createTopic(config().kafkaPayloadProducer.topic)
        KafkaTestContainer.createTopic(config().kafkaPayloadReceiver.topic)
        KafkaTestContainer.createTopic(config().kafkaSignalProducer.topic)
        KafkaTestContainer.createTopic(config().kafkaSignalReceiver.topic)
        KafkaTestContainer.createTopic(config().kafkaErrorQueue.topic)

        File(localTmp(KAFKA_BROKERS_STORAGE)).writeText(KafkaTestContainer.bootstrapServers)
    }

    // NEED TO CLOSE THIS PROPERLY IN ORDER TO CLEAN UP AND BE ABLE TO START IT AGAIN
    println("=== Running test servers until someone writes stop<return> in this window ==")
    if (START_MOCK_OAUTH) println("=== OAuth2 URL: ${mockOAuth2Server?.baseUrl()}")
    if (START_POSTGRES) println("=== DB URL: ${ebmsProviderDbContainer?.jdbcUrl}")
    if (START_KAFKA) println("=== Kafka URL: ${KafkaTestContainer.bootstrapServers}")
    var stopped = false
    while (!stopped) {
        print("Type 'stop' and <return> to stop >")
        val line = readln()
        stopped = (line == "stop")
    }

    KafkaTestContainer.stop()
    ebmsProviderDbContainer?.stop()
    mockOAuth2Server?.shutdown()
}

fun getRunningKafkaBrokerUrl(): String {
    return File(localTmp(KAFKA_BROKERS_STORAGE)).readText(Charsets.UTF_8)
}

fun getRunningPostgresConfiguration(): HikariConfig {
    val currentUrl = File(localTmp(POSTGRES_JDBCURL_STORAGE)).readText(Charsets.UTF_8)
    return HikariConfig().apply {
        jdbcUrl = currentUrl
        username = POSTGRES_TEST_USER
        password = POSTGRES_TEST_PW
        maximumPoolSize = 5
        minimumIdle = 1
        idleTimeout = 500001
        connectionTimeout = 10000
        maxLifetime = 600001
        initializationFailTimeout = 5000
    }
}
