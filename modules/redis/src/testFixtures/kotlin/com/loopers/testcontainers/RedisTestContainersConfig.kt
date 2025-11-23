package com.loopers.testcontainers

import com.redis.testcontainers.RedisContainer
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration

@Configuration
class RedisTestContainersConfig : ApplicationContextInitializer<ConfigurableApplicationContext> {
    companion object {
        private val redisContainer = RedisContainer("redis:latest")
            .apply {
                start()
            }
    }

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        TestPropertyValues.of(
            "datasource.redis.database=0",
            "datasource.redis.master.host=${redisContainer.host}",
            "datasource.redis.master.port=${redisContainer.firstMappedPort}",
            "datasource.redis.replicas[0].host=${redisContainer.host}",
            "datasource.redis.replicas[0].port=${redisContainer.firstMappedPort}",
        ).applyTo(applicationContext.environment)
    }
}
