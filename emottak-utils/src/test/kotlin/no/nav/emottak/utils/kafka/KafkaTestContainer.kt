package no.nav.emottak.utils.kafka

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

object KafkaTestContainer {
    private val kafkaContainer: KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))

    val bootstrapServers: String
        get() = kafkaContainer.bootstrapServers

    fun start() {
        kafkaContainer.start()
    }

    fun stop() {
        kafkaContainer.stop()
    }

    fun createTopic(topicName: String, partitions: Int = 1, replicationFactor: Short = 1) {
        val config = mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers
        )

        AdminClient.create(config).use { adminClient ->
            val newTopic = NewTopic(topicName, partitions, replicationFactor)
            adminClient.createTopics(listOf(newTopic)).all().get()
        }
    }
}
