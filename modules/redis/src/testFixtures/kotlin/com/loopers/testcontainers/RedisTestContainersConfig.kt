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

        init {
            // companion object의 init 블록은 클래스 로드 시점에 실행됨
            // Spring이 설정을 읽기 전에 System Property가 설정됨
            System.setProperty("datasource.redis.database", "0")
            System.setProperty("datasource.redis.master.host", redisContainer.host)
            System.setProperty("datasource.redis.master.port", redisContainer.firstMappedPort.toString())
            System.setProperty("datasource.redis.replicas[0].host", redisContainer.host)
            System.setProperty("datasource.redis.replicas[0].port", redisContainer.firstMappedPort.toString())
        }
    }
}
