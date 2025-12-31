package com.loopers.infrastructure.ranking

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.TopicConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaAdmin

@Configuration
class RankingEventTopicConfig {

    companion object {
        private const val PARTITIONS = 3
        private const val REPLICAS = 3
        private const val MIN_ISR = "2"
    }

    @Bean
    fun rankingEventTopics(): KafkaAdmin.NewTopics {
        return KafkaAdmin.NewTopics(
            buildTopic("ranking-events"),
            buildTopic("ranking-events.DLT"),
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
