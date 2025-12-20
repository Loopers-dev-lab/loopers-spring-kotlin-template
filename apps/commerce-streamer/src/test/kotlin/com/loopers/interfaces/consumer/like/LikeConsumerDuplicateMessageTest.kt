package com.loopers.interfaces.consumer.like

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.productMetric.ProductMetricFacade
import com.loopers.event.CatalogEventPayload
import com.loopers.event.CatalogType
import com.loopers.event.EventType
import com.loopers.infrastructure.event.EventHandleJpaRepository
import com.loopers.infrastructure.productMetric.ProductMetricJpaRepository
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestConstructor

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import(MySqlTestContainersConfig::class)
@DisplayName("LikeConsumer 중복 메시지 재전송 테스트")
class LikeConsumerDuplicateMessageTest(
        private val productMetricFacade: ProductMetricFacade,
        private val productMetricJpaRepository: ProductMetricJpaRepository,
        private val eventHandleJpaRepository: EventHandleJpaRepository,
        private val objectMapper: ObjectMapper,
        private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("중복 메시지 재전송 시 최종 결과가 한 번만 반영되는지 확인")
    @Test
    fun duplicateMessageRetransmission_finalResultAppliedOnlyOnce() {
        // arrange
        val productId = 1L
        val userId = 1L
        val eventId = "test-event-id-123"

        val payload =
                CatalogEventPayload(
                        eventId = eventId,
                        productId = productId,
                        userId = userId,
                        type = CatalogType.LIKED,
                )

        val payloadJson = objectMapper.writeValueAsString(payload)

        val firstMessage =
                ConsumerRecord(
                        EventType.CATALOG_EVENT.topic,
                        0,
                        0L,
                        "key",
                        payloadJson,
                )

        val secondMessage =
                ConsumerRecord(
                        EventType.CATALOG_EVENT.topic,
                        0,
                        1L,
                        "key",
                        payloadJson,
                )

        // act - 첫 번째 메시지 처리
        productMetricFacade.updateProductMetrics(listOf(firstMessage))

        // 첫 번째 처리 후 상태 확인
        val firstProductMetric = productMetricJpaRepository.findByRefProductId(productId)
        assertThat(firstProductMetric).isNotNull
        assertThat(firstProductMetric!!.likeCount).isEqualTo(1L)

        val firstEventHandle = eventHandleJpaRepository.findByEventId(eventId)
        assertThat(firstEventHandle).isNotNull()
        assertThat(firstEventHandle!!.eventId).isEqualTo(eventId)

        // act - 두 번째 메시지 처리 (중복 메시지 재전송 시뮬레이션)
        productMetricFacade.updateProductMetrics(listOf(secondMessage))

        // assert - 최종 결과가 한 번만 반영되었는지 확인
        val finalProductMetric = productMetricJpaRepository.findByRefProductId(productId)
        assertThat(finalProductMetric).isNotNull
        assertThat(finalProductMetric!!.likeCount).isEqualTo(1L)

        // assert - EventHandle은 여전히 하나만 존재해야 함
        val allEventHandles = eventHandleJpaRepository.findAll()
        assertThat(allEventHandles).hasSize(1)
    }

    @DisplayName("같은 배치 내에서 중복 eventId가 있는 경우 한 번만 처리되는지 확인")
    @Test
    fun duplicateEventIdInSameBatch_processedOnlyOnce() {
        // arrange
        val productId = 2L
        val userId = 2L
        val eventId = "test-event-id-456"

        val payload =
                CatalogEventPayload(
                        eventId = eventId,
                        productId = productId,
                        userId = userId,
                        type = CatalogType.LIKED,
                )

        val payloadJson = objectMapper.writeValueAsString(payload)

        // 같은 eventId를 가진 메시지 3개 생성
        val message1 =
                ConsumerRecord(
                        EventType.CATALOG_EVENT.topic,
                        0,
                        0L,
                        "key",
                        payloadJson,
                )

        val message2 =
                ConsumerRecord(
                        EventType.CATALOG_EVENT.topic,
                        0,
                        1L,
                        "key",
                        payloadJson,
                )

        val message3 =
                ConsumerRecord<String, String>(
                        "CATALOG_EVENT",
                        0,
                        2L,
                        "key",
                        payloadJson,
                )

        // act - 같은 배치에 중복 메시지가 포함된 경우
        productMetricFacade.updateProductMetrics(listOf(message1, message2, message3))

        val productMetric = productMetricJpaRepository.findByRefProductId(productId)
        assertThat(productMetric).isNotNull
        assertThat(productMetric!!.likeCount).isEqualTo(1L)

        val eventHandles = eventHandleJpaRepository.findAll()
        assertThat(eventHandles).hasSize(1)
    }

    @DisplayName("서로 다른 eventId를 가진 메시지들은 모두 처리되는지 확인")
    @Test
    fun differentEventIds_allProcessed() {
        // arrange
        val productId = 3L
        val userId = 3L

        val payload1 =
                CatalogEventPayload(
                        eventId = "event-id-1",
                        productId = productId,
                        userId = userId,
                        type = CatalogType.LIKED,
                )

        val payload2 =
                CatalogEventPayload(
                        eventId = "event-id-2",
                        productId = productId,
                        userId = userId,
                        type = CatalogType.LIKED,
                )

        val payload3 =
                CatalogEventPayload(
                        eventId = "event-id-3",
                        productId = productId,
                        userId = userId,
                        type = CatalogType.LIKED,
                )

        val message1 =
                ConsumerRecord<String, String>(
                        "CATALOG_EVENT",
                        0,
                        0L,
                        "key",
                        objectMapper.writeValueAsString(payload1),
                )

        val message2 =
                ConsumerRecord<String, String>(
                        "CATALOG_EVENT",
                        0,
                        1L,
                        "key",
                        objectMapper.writeValueAsString(payload2),
                )

        val message3 =
                ConsumerRecord<String, String>(
                        "CATALOG_EVENT",
                        0,
                        2L,
                        "key",
                        objectMapper.writeValueAsString(payload3),
                )

        // act
        productMetricFacade.updateProductMetrics(listOf(message1, message2, message3))

        val productMetric = productMetricJpaRepository.findByRefProductId(productId)
        assertThat(productMetric).isNotNull
        assertThat(productMetric!!.likeCount).isEqualTo(3L)

        val eventHandles = eventHandleJpaRepository.findAll()
        assertThat(eventHandles).hasSize(3)
    }
}
        // act
        productMetricFacade.updateProductMetrics(listOf(message1, message2, message3))
        
        // assert - 모든 메시지가 처리되어야 함
        val productMetric = productMetricJpaRepository.findByRefProductId(productId)
        assertThat(productMetric).isNotNull
        assertThat(productMetric!!.likeCount)
            .`as`("서로 다른 eventId를 가진 메시지들은 모두 처리되어야 함")
            .isEqualTo(3L)
        
        // assert - EventHandle도 3개 생성되어야 함
        val eventHandles = eventHandleJpaRepository.findAll()
        assertThat(eventHandles)
            .`as`("서로 다른 eventId에 대한 EventHandle은 각각 생성되어야 함")
            .hasSize(3)
    }
}
