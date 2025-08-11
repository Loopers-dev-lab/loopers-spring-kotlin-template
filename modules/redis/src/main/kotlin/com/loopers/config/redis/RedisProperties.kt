package com.loopers.config.redis

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(value = "datasource.redis")
data class RedisProperties(
    val database: Int,
    val master: RedisNodeInfo,
    val replicas: List<RedisNodeInfo>,
)
