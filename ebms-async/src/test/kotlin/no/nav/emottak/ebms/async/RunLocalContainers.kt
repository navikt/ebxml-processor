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

const val START_KAFKA = true
const val KAFKA_BROKERS_STORAGE = "/tmp/kafka_brokers.txt" // use this to avoid needing to copy URL from this process' log to clients

const val START_POSTGRES = true
const val POSTGRES_TEST_USER = "emottak-ebms-db-admin"
const val POSTGRES_TEST_PW = "test"
const val POSTGRES_JDBCURL_STORAGE = "/tmp/postgres_jdbcurl.txt" // use this to avoid needing to copy URL from this process' log to clients

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

        if (POSTGRES_JDBCURL_STORAGE != null) {
            File(POSTGRES_JDBCURL_STORAGE).writeText(ebmsProviderDbContainer.jdbcUrl)
        }
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

        if (KAFKA_BROKERS_STORAGE != null) {
            File(KAFKA_BROKERS_STORAGE).writeText(KafkaTestContainer.bootstrapServers)
        }
    }

    println("=== Running test servers until someone writes stop<return> in this window ==")
    if (START_MOCK_OAUTH) println("=== OAuth2 URL: ${mockOAuth2Server?.baseUrl()}")
    if (START_POSTGRES) println("=== DB URL: ${ebmsProviderDbContainer?.jdbcUrl}")
    if (START_KAFKA) println("=== Kafka URL: ${KafkaTestContainer.bootstrapServers}")
//    Thread.sleep(1000000000)
    var stopped = false
    while (!stopped) {
        print("Type 'stop' and <return> to stop >")
        val line = readln()
        stopped = line == "stop"
    }

    KafkaTestContainer.stop()
    mockOAuth2Server?.shutdown()
}

fun getRunningKafkaBrokerUrl(): String {
    return File(KAFKA_BROKERS_STORAGE).readText(Charsets.UTF_8)
}

fun getRunningPostgresConfiguration(): HikariConfig {
    val currentUrl = File(POSTGRES_JDBCURL_STORAGE).readText(Charsets.UTF_8)
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
