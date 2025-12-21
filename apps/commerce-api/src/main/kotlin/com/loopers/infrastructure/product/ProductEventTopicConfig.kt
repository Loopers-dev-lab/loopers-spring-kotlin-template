package com.loopers.infrastructure.product

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.TopicConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaAdmin

@Configuration
class ProductEventTopicConfig {

    companion object {
        private const val PARTITIONS = 3
        private const val REPLICAS = 3
        private const val MIN_ISR = "2"
    }

    @Bean
    fun productEventTopics(): KafkaAdmin.NewTopics {
        return KafkaAdmin.NewTopics(
            buildTopic("product-events"),
            buildTopic("product-events.DLT"),
            buildTopic("stock-events"),
            buildTopic("stock-events.DLT"),
        )
    }

    private fun buildTopic(name: String): NewTopic {
        return TopicBuilder.name(name)
            .partitions(PARTITIONS)
            .replicas(REPLICAS)
            .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, MIN_ISR)
            .build()
    }
}
