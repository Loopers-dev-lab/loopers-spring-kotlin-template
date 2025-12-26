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
            // 테스트 환경에서는 earliest로 설정하여 메시지 유실 방지
            System.setProperty("spring.kafka.consumer.auto-offset-reset", "earliest")
        }
    }
}
