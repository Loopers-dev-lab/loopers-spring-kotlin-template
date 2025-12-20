package com.loopers.infrastructure.outbox

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "outbox.relay")
data class OutboxRelayProperties(
    val batchSize: Int = 100,
    val sendTimeoutSeconds: Long = 5,
    val retryIntervalSeconds: Long = 10,
    val maxAgeMinutes: Long = 5,
)
