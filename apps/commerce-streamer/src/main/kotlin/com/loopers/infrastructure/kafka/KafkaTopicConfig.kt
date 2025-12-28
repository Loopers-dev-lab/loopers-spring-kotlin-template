package com.loopers.infrastructure.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate

/**
 * Kafka 토픽 설정
 * commerce-streamer는 Consumer만 사용 (Producer는 commerce-api에서 담당)
 */
@Configuration
@ConditionalOnBean(KafkaTemplate::class)
class KafkaTopicConfig {

    @Value("\${kafka.topics.catalog-events}")
    private lateinit var catalogEventsTopic: String

    @Value("\${kafka.topics.order-events}")
    private lateinit var orderEventsTopic: String

    @Value("\${kafka.topics.catalog-events-dlq}")
    private lateinit var catalogEventsDlqTopic: String

    @Value("\${kafka.topics.order-events-dlq}")
    private lateinit var orderEventsDlqTopic: String

    /**
     * catalog-events 토픽 생성
     */
    @Bean
    fun catalogEventsTopic(): NewTopic {
        return TopicBuilder.name(catalogEventsTopic)
            .partitions(3) // 파티션 수
            .replicas(1) // 복제본 수 (로컬 환경)
            .build()
    }

    /**
     * order-events 토픽 생성
     */
    @Bean
    fun orderEventsTopic(): NewTopic {
        return TopicBuilder.name(orderEventsTopic)
            .partitions(3)
            .replicas(1)
            .build()
    }

    /**
     * catalog-events DLQ 토픽 생성
     */
    @Bean
    fun catalogEventsDlqTopic(): NewTopic {
        return TopicBuilder.name(catalogEventsDlqTopic)
            .partitions(1)
            .replicas(1)
            .build()
    }

    /**
     * order-events DLQ 토픽 생성
     */
    @Bean
    fun orderEventsDlqTopic(): NewTopic {
        return TopicBuilder.name(orderEventsDlqTopic)
            .partitions(1)
            .replicas(1)
            .build()
    }
}
