package com.loopers.testcontainers

import org.springframework.context.annotation.Configuration
import org.testcontainers.kafka.ConfluentKafkaContainer

@Configuration
class KafkaTestContainersConfig {
    companion object {
        private val kafkaContainer: ConfluentKafkaContainer =
            ConfluentKafkaContainer("confluentinc/cp-kafka:7.5.0")
                .apply {
                    start()
                }

        init {
            // Spring Kafka 표준 프로퍼티 직접 설정 (YAML보다 높은 우선순위)
            System.setProperty("spring.kafka.bootstrap-servers", kafkaContainer.bootstrapServers)
            System.setProperty("spring.kafka.admin.properties.bootstrap.servers", kafkaContainer.bootstrapServers)
        }
    }
}
