package com.loopers.config.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.kafka.consumer")
data class KafkaConsumerProperties(
    val maxPollRecords: Int = 500,
    val fetchMinBytes: Int = 1048576,
    val fetchMaxWaitMs: Int = 5000,
    val sessionTimeoutMs: Int = 60000,
    val heartbeatIntervalMs: Int = 20000,
    val maxPollIntervalMs: Int = 120000,
)
