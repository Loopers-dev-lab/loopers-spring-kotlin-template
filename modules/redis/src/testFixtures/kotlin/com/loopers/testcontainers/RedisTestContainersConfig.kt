package com.loopers.testcontainers

import com.redis.testcontainers.RedisContainer
import org.springframework.context.annotation.Configuration

@Configuration
class RedisTestContainersConfig {
    companion object {
        private val redisContainer = RedisContainer("redis:latest")
            .apply {
                start()
            }
    }

    init {
        System.setProperty("datasource.redis.database", "0")
        System.setProperty("datasource.redis.master.host", redisContainer.host)
        System.setProperty("datasource.redis.master.port", redisContainer.firstMappedPort.toString())
        System.setProperty("datasource.redis.replicas[0].host", redisContainer.host)
        System.setProperty("datasource.redis.replicas[0].port", redisContainer.firstMappedPort.toString())
    }
}
