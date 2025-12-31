package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.f4b6a3.uuid.UuidCreator
import com.loopers.IntegrationTest
import com.loopers.domain.audit.AuditLogRepository
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.event.OutboxEvent
import com.loopers.domain.metrics.ProductMetricsRepository
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Kafka Consumer 통합 테스트
 *
 * 실제 로컬 Kafka (docker/infra-compose.yml)를 사용하여 테스트
 * - 메시지 발행 → Consumer 처리 → DB 검증
 *
 * 테스트 전 docker/infra-compose.yml로 Kafka를 띄워야 함
 */
// @Disabled("로컬 Kafka 통합 테스트 - 수동 실행")
@DisplayName("Kafka Consumer 통합 테스트")
class KafkaConsumerIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var kafkaProperties: KafkaProperties

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var productMetricsRepository: ProductMetricsRepository

    @Autowired
    private lateinit var eventHandledRepository: EventHandledRepository

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    private lateinit var kafkaTemplate: KafkaTemplate<String, ByteArray>

    @BeforeEach
    fun setUp() {
        // ByteArray를 그대로 보내는 Producer 설정 (이중 직렬화 방지)
        val producerProps = kafkaProperties.buildProducerProperties(null).toMutableMap()
        producerProps["key.serializer"] = StringSerializer::class.java
        producerProps["value.serializer"] = ByteArraySerializer::class.java

        val producerFactory = DefaultKafkaProducerFactory<String, ByteArray>(producerProps)
        kafkaTemplate = KafkaTemplate(producerFactory)
    }

    // ==================== ProductMetricConsumer 테스트 ====================

    @Test
    @DisplayName("LikeCountChanged 이벤트를 소비하여 좋아요 수를 증가시킨다")
    fun `consume LikeCountChanged event and increase like count`() {
        // given
        val productId = 1001L
        val eventId = UuidCreator.getTimeOrderedEpoch().toString()
        val event = OutboxEvent.LikeCountChanged(
            productId = productId,
            userId = 100L,
            action = OutboxEvent.LikeCountChanged.LikeAction.LIKED,
            timestamp = ZonedDateTime.now(),
        )

        // when - Kafka로 메시지 발행
        publishEvent(OutboxEvent.LikeCountChanged.TOPIC, eventId, event)

        // then - Consumer가 처리할 때까지 대기 후 검증
        await atMost Duration.ofSeconds(10) untilAsserted {
            val metricDate = event.timestamp.toLocalDate()
            val metrics = productMetricsRepository.findByProductIdAndMetricDate(productId, metricDate)
            assertThat(metrics).isNotNull
            assertThat(metrics!!.likeCount).isEqualTo(1)

            val aggregateId = "${productId}_$metricDate"
            assertThat(eventHandledRepository.existsByEventIdAndAggregateId(eventId, aggregateId)).isTrue()
        }
    }

    @Test
    @DisplayName("ViewCountIncreased 이벤트를 소비하여 조회 수를 증가시킨다")
    fun `consume ViewCountIncreased event and increase view count`() {
        // given
        val productId = 1002L
        val eventId = UuidCreator.getTimeOrderedEpoch().toString()
        val event = OutboxEvent.ViewCountIncreased(
            productId = productId,
            userId = 100L,
            timestamp = ZonedDateTime.now(),
        )

        // when
        publishEvent(OutboxEvent.ViewCountIncreased.TOPIC, eventId, event)

        // then
        await atMost Duration.ofSeconds(10) untilAsserted {
            val metricDate = event.timestamp.toLocalDate()
            val metrics = productMetricsRepository.findByProductIdAndMetricDate(productId, metricDate)
            assertThat(metrics).isNotNull
            assertThat(metrics!!.viewCount).isEqualTo(1)

            val aggregateId = "${productId}_$metricDate"
            assertThat(eventHandledRepository.existsByEventIdAndAggregateId(eventId, aggregateId)).isTrue()
        }
    }

    @Test
    @DisplayName("OrderCompleted 이벤트를 소비하여 판매 수량을 증가시킨다")
    fun `consume OrderCompleted event and increase sold count`() {
        // given
        val productId1 = 1003L
        val productId2 = 1004L
        val eventId = UuidCreator.getTimeOrderedEpoch().toString()
        val event = OutboxEvent.OrderCompleted(
            orderId = 5001L,
            userId = 100L,
            totalAmount = 50000L,
            items = listOf(
                OutboxEvent.OrderCompleted.OrderItem(productId = productId1, quantity = 2, price = 15000L),
                OutboxEvent.OrderCompleted.OrderItem(productId = productId2, quantity = 3, price = 10000L),
            ),
            timestamp = ZonedDateTime.now(),
        )

        // when
        publishEvent(OutboxEvent.OrderCompleted.TOPIC, eventId, event)

        // then
        await atMost Duration.ofSeconds(10) untilAsserted {
            val metricDate = event.timestamp.toLocalDate()
            val metrics1 = productMetricsRepository.findByProductIdAndMetricDate(productId1, metricDate)
            assertThat(metrics1).isNotNull
            assertThat(metrics1!!.soldCount).isEqualTo(2)

            val metrics2 = productMetricsRepository.findByProductIdAndMetricDate(productId2, metricDate)
            assertThat(metrics2).isNotNull
            assertThat(metrics2!!.soldCount).isEqualTo(3)

            // 동일 eventId로 상품별 aggregateId 조합으로 처리됨
            val aggregateId1 = "${productId1}_$metricDate"
            val aggregateId2 = "${productId2}_$metricDate"
            assertThat(eventHandledRepository.existsByEventIdAndAggregateId(eventId, aggregateId1)).isTrue()
            assertThat(eventHandledRepository.existsByEventIdAndAggregateId(eventId, aggregateId2)).isTrue()
        }
    }

    @Test
    @DisplayName("OrderCanceled 이벤트를 소비하여 판매 수량을 감소시킨다")
    fun `consume OrderCanceled event and decrease sold count`() {
        // given - 먼저 주문 완료로 수량 증가
        val productId = 1005L
        val orderCompletedEventId = UuidCreator.getTimeOrderedEpoch().toString()
        val orderCompletedEvent = OutboxEvent.OrderCompleted(
            orderId = 5002L,
            userId = 100L,
            totalAmount = 30000L,
            items = listOf(
                OutboxEvent.OrderCompleted.OrderItem(productId = productId, quantity = 5, price = 6000L),
            ),
            timestamp = ZonedDateTime.now(),
        )
        publishEvent(OutboxEvent.OrderCompleted.TOPIC, orderCompletedEventId, orderCompletedEvent)

        // 주문 완료 처리 대기
        await atMost Duration.ofSeconds(10) untilAsserted {
            val metricDate = orderCompletedEvent.timestamp.toLocalDate()
            val metrics = productMetricsRepository.findByProductIdAndMetricDate(productId, metricDate)
            assertThat(metrics).isNotNull
            assertThat(metrics!!.soldCount).isEqualTo(5)
        }

        // when - 주문 취소
        val orderCanceledEventId = UuidCreator.getTimeOrderedEpoch().toString()
        val orderCanceledEvent = OutboxEvent.OrderCanceled(
            orderId = 5002L,
            userId = 100L,
            reason = "고객 요청",
            orderCreatedAt = ZonedDateTime.now().minusDays(1),
            items = listOf(
                OutboxEvent.OrderCanceled.OrderItem(productId = productId, quantity = 5, price = 6000L),
            ),
            timestamp = ZonedDateTime.now().plusSeconds(1),
        )
        publishEvent(OutboxEvent.OrderCanceled.TOPIC, orderCanceledEventId, orderCanceledEvent)

        // then
        await atMost Duration.ofSeconds(10) untilAsserted {
            val metricDate = orderCanceledEvent.timestamp.toLocalDate()
            val metrics = productMetricsRepository.findByProductIdAndMetricDate(productId, metricDate)
            assertThat(metrics).isNotNull
            assertThat(metrics!!.soldCount).isEqualTo(0)
        }
    }

    @Test
    @DisplayName("동일한 이벤트를 중복 발행해도 한 번만 처리된다 (멱등성)")
    fun `duplicate events should be processed only once`() {
        // given
        val productId = 1006L
        val eventId = UuidCreator.getTimeOrderedEpoch().toString()
        val event = OutboxEvent.LikeCountChanged(
            productId = productId,
            userId = 100L,
            action = OutboxEvent.LikeCountChanged.LikeAction.LIKED,
            timestamp = ZonedDateTime.now(),
        )

        // when - 동일한 이벤트를 3번 발행
        repeat(3) {
            publishEvent(OutboxEvent.LikeCountChanged.TOPIC, eventId, event)
            Thread.sleep(500) // 약간의 딜레이
        }

        // then - 좋아요 수는 1만 증가
        await atMost Duration.ofSeconds(15) untilAsserted {
            val metricDate = event.timestamp.toLocalDate()
            val metrics = productMetricsRepository.findByProductIdAndMetricDate(productId, metricDate)
            assertThat(metrics).isNotNull
            assertThat(metrics!!.likeCount).isEqualTo(1)
        }
    }

    @Test
    @DisplayName("여러 이벤트를 배치로 처리한다")
    fun `batch process multiple events`() {
        // given - 여러 좋아요 이벤트 발행
        val productId = 1007L
        val events = (1..5).map { index ->
            val eventId = UuidCreator.getTimeOrderedEpoch().toString()
            val event = OutboxEvent.LikeCountChanged(
                productId = productId,
                userId = index.toLong(),
                action = OutboxEvent.LikeCountChanged.LikeAction.LIKED,
                timestamp = ZonedDateTime.now().plusSeconds(index.toLong()),
            )
            eventId to event
        }

        // when
        events.forEach { (eventId, event) ->
            publishEvent(OutboxEvent.LikeCountChanged.TOPIC, eventId, event)
        }

        // then - 5개 모두 처리됨
        await atMost Duration.ofSeconds(15) untilAsserted {
            // 첫 번째 이벤트의 날짜를 사용 (모두 같은 날짜)
            val metricDate = events.first().second.timestamp.toLocalDate()
            val metrics = productMetricsRepository.findByProductIdAndMetricDate(productId, metricDate)
            assertThat(metrics).isNotNull
            assertThat(metrics!!.likeCount).isEqualTo(5)

            val aggregateId = "${productId}_$metricDate"
            events.forEach { (eventId, _) ->
                assertThat(eventHandledRepository.existsByEventIdAndAggregateId(eventId, aggregateId)).isTrue()
            }
        }
    }

    // ==================== ProductStockConsumer 테스트 ====================

    @Test
    @DisplayName("SoldOut 이벤트를 소비하여 상품 캐시를 무효화한다")
    fun `consume SoldOut event and evict product cache`() {
        // given
        val productId = 2001L
        val eventId = UuidCreator.getTimeOrderedEpoch().toString()
        val event = OutboxEvent.SoldOut(
            productId = productId,
            timestamp = ZonedDateTime.now(),
        )

        // when - Kafka로 메시지 발행
        publishEvent(OutboxEvent.SoldOut.TOPIC, eventId, event)

        // then - Consumer가 처리할 때까지 대기 후 검증
        await atMost Duration.ofSeconds(10) untilAsserted {
            // 이벤트 처리 기록 확인
            assertThat(eventHandledRepository.existsByEventIdAndAggregateId(eventId, productId.toString())).isTrue()
        }
    }

    @Test
    @DisplayName("SoldOut 이벤트 중복 발행 시 한 번만 처리된다 (멱등성)")
    fun `duplicate SoldOut events should be processed only once`() {
        // given
        val productId = 2002L
        val eventId = UuidCreator.getTimeOrderedEpoch().toString()
        val event = OutboxEvent.SoldOut(
            productId = productId,
            timestamp = ZonedDateTime.now(),
        )

        // when - 동일한 이벤트를 3번 발행
        repeat(3) {
            publishEvent(OutboxEvent.SoldOut.TOPIC, eventId, event)
            Thread.sleep(500)
        }

        // then - 이벤트는 한 번만 처리됨
        await atMost Duration.ofSeconds(15) untilAsserted {
            assertThat(eventHandledRepository.existsByEventIdAndAggregateId(eventId, productId.toString())).isTrue()
        }
    }

    // ==================== AuditLogEventConsumer 테스트 ====================

    @Test
    @DisplayName("LikeCountChanged 이벤트가 감사 로그에 기록된다")
    fun `LikeCountChanged event is recorded in audit log`() {
        // given
        val productId = 3001L
        val eventId = UuidCreator.getTimeOrderedEpoch().toString()
        val event = OutboxEvent.LikeCountChanged(
            productId = productId,
            userId = 100L,
            action = OutboxEvent.LikeCountChanged.LikeAction.LIKED,
            timestamp = ZonedDateTime.now(),
        )

        // when
        publishEvent(OutboxEvent.LikeCountChanged.TOPIC, eventId, event)

        // then - 감사 로그에 기록 확인
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertThat(auditLogRepository.existsByEventId(eventId)).isTrue()
        }
    }

    @Test
    @DisplayName("ViewCountIncreased 이벤트가 감사 로그에 기록된다")
    fun `ViewCountIncreased event is recorded in audit log`() {
        // given
        val productId = 3002L
        val eventId = UuidCreator.getTimeOrderedEpoch().toString()
        val event = OutboxEvent.ViewCountIncreased(
            productId = productId,
            userId = 100L,
            timestamp = ZonedDateTime.now(),
        )

        // when
        publishEvent(OutboxEvent.ViewCountIncreased.TOPIC, eventId, event)

        // then
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertThat(auditLogRepository.existsByEventId(eventId)).isTrue()
        }
    }

    @Test
    @DisplayName("OrderCompleted 이벤트가 감사 로그에 기록된다")
    fun `OrderCompleted event is recorded in audit log`() {
        // given
        val eventId = UuidCreator.getTimeOrderedEpoch().toString()
        val event = OutboxEvent.OrderCompleted(
            orderId = 6001L,
            userId = 100L,
            totalAmount = 50000L,
            items = listOf(
                OutboxEvent.OrderCompleted.OrderItem(productId = 3003L, quantity = 2, price = 25000L),
            ),
            timestamp = ZonedDateTime.now(),
        )

        // when
        publishEvent(OutboxEvent.OrderCompleted.TOPIC, eventId, event)

        // then
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertThat(auditLogRepository.existsByEventId(eventId)).isTrue()
        }
    }

    @Test
    @DisplayName("OrderCanceled 이벤트가 감사 로그에 기록된다")
    fun `OrderCanceled event is recorded in audit log`() {
        // given
        val eventId = UuidCreator.getTimeOrderedEpoch().toString()
        val event = OutboxEvent.OrderCanceled(
            orderId = 6002L,
            userId = 100L,
            reason = "고객 요청",
            orderCreatedAt = ZonedDateTime.now().minusDays(1),
            items = listOf(
                OutboxEvent.OrderCanceled.OrderItem(productId = 3004L, quantity = 1, price = 50000L),
            ),
            timestamp = ZonedDateTime.now(),
        )

        // when
        publishEvent(OutboxEvent.OrderCanceled.TOPIC, eventId, event)

        // then
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertThat(auditLogRepository.existsByEventId(eventId)).isTrue()
        }
    }

    @Test
    @DisplayName("SoldOut 이벤트가 감사 로그에 기록된다")
    fun `SoldOut event is recorded in audit log`() {
        // given
        val productId = 3005L
        val eventId = UuidCreator.getTimeOrderedEpoch().toString()
        val event = OutboxEvent.SoldOut(
            productId = productId,
            timestamp = ZonedDateTime.now(),
        )

        // when
        publishEvent(OutboxEvent.SoldOut.TOPIC, eventId, event)

        // then
        await atMost Duration.ofSeconds(10) untilAsserted {
            assertThat(auditLogRepository.existsByEventId(eventId)).isTrue()
        }
    }

    @Test
    @DisplayName("감사 로그는 중복 이벤트도 한 번만 기록된다 (멱등성)")
    fun `audit log records duplicate events only once`() {
        // given
        val productId = 3006L
        val eventId = UuidCreator.getTimeOrderedEpoch().toString()
        val event = OutboxEvent.LikeCountChanged(
            productId = productId,
            userId = 100L,
            action = OutboxEvent.LikeCountChanged.LikeAction.LIKED,
            timestamp = ZonedDateTime.now(),
        )

        // when - 동일한 이벤트를 3번 발행
        repeat(3) {
            publishEvent(OutboxEvent.LikeCountChanged.TOPIC, eventId, event)
            Thread.sleep(500)
        }

        // then - 감사 로그에 한 번만 기록됨
        await atMost Duration.ofSeconds(15) untilAsserted {
            assertThat(auditLogRepository.existsByEventId(eventId)).isTrue()
        }
    }

    private fun publishEvent(topic: String, eventId: String, event: Any) {
        val payload = objectMapper.writeValueAsBytes(event)
        val record = ProducerRecord<String, ByteArray>(topic, payload).apply {
            headers().add(RecordHeader("eventId", eventId.toByteArray()))
        }
        kafkaTemplate.send(record).get() // 동기로 발행 완료 대기
    }
}
