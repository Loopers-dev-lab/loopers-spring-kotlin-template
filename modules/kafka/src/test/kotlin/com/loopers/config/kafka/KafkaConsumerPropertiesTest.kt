package com.loopers.config.kafka

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals

@DisplayName("KafkaConsumerProperties 단위 테스트")
class KafkaConsumerPropertiesTest {

    @DisplayName("기본값 테스트")
    @Nested
    inner class DefaultValues {

        @DisplayName("모든 프로퍼티가 기본값을 가진다")
        @Test
        fun `all properties have default values`() {
            // when
            val properties = KafkaConsumerProperties()

            // then
            assertEquals(500, properties.maxPollRecords)
            assertEquals(1048576, properties.fetchMinBytes)
            assertEquals(5000, properties.fetchMaxWaitMs)
            assertEquals(60000, properties.sessionTimeoutMs)
            assertEquals(20000, properties.heartbeatIntervalMs)
            assertEquals(120000, properties.maxPollIntervalMs)
        }
    }

    @DisplayName("프로퍼티 바인딩 테스트")
    @Nested
    @SpringBootTest(classes = [KafkaConsumerPropertiesTestConfig::class])
    @TestPropertySource(
        properties = [
            "app.kafka.consumer.max-poll-records=1000",
            "app.kafka.consumer.fetch-min-bytes=2097152",
            "app.kafka.consumer.fetch-max-wait-ms=10000",
            "app.kafka.consumer.session-timeout-ms=120000",
            "app.kafka.consumer.heartbeat-interval-ms=40000",
            "app.kafka.consumer.max-poll-interval-ms=240000",
        ],
    )
    inner class PropertyBinding {

        @Autowired
        private lateinit var properties: KafkaConsumerProperties

        @DisplayName("application.yml 설정이 프로퍼티에 바인딩된다")
        @Test
        fun `properties are bound from configuration`() {
            // then
            assertEquals(1000, properties.maxPollRecords)
            assertEquals(2097152, properties.fetchMinBytes)
            assertEquals(10000, properties.fetchMaxWaitMs)
            assertEquals(120000, properties.sessionTimeoutMs)
            assertEquals(40000, properties.heartbeatIntervalMs)
            assertEquals(240000, properties.maxPollIntervalMs)
        }
    }
}

@EnableConfigurationProperties(KafkaConsumerProperties::class)
class KafkaConsumerPropertiesTestConfig
