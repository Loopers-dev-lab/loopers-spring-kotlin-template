package com.loopers.utils

import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.stereotype.Component

@Component
class RedisCleanUp(
    private val redisConnectionFactory: RedisConnectionFactory,
) {
    fun truncateAll() {
        redisConnectionFactory.connection.use { it.serverCommands().flushAll() }
    }
}
