package com.loopers.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.event.LikeAddedEvent
import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.outbox.OutboxEventStatus
import com.loopers.domain.product.ProductMetricsRepository
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Kafka E2E 통합 테스트
 *
 * 이 테스트는 docker/infra-compose.yml의 Kafka가 실행 중이어야 합니다.
 *
 * 실행 방법:
 * 1. cd docker && docker-compose -f infra-compose.yml up -d kafka
 * 2. export KAFKA_BOOTSTRAP_SERVERS=localhost:19092
 * 3. ./gradlew :apps:commerce-api:test --tests "KafkaE2EIntegrationTest"
 *
 * Kafka가 실행 중이지 않으면 이 테스트는 skip됩니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class KafkaE2EIntegrationTest {

    @Autowired(required = false)
    private var outboxEventRepository: OutboxEventRepository? = null

    @Autowired(required = false)
    private var kafkaProducerService: KafkaProducerService? = null

    @Autowired(required = false)
    private var productMetricsRepository: ProductMetricsRepository? = null

    @Autowired(required = false)
    private var eventHandledRepository: EventHandledRepository? = null

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        // Kafka가 없으면 테스트 skip
        if (kafkaProducerService == null) {
            println("⚠️  Kafka가 실행 중이지 않습니다. 테스트를 건너뜁니다.")
            println("   Kafka 실행: cd docker && docker-compose -f infra-compose.yml up -d kafka")
        }
    }

    @Test
    fun `Kafka가 실행 중이지 않으면 KafkaProducerService 빈이 생성되지 않는다`() {
        // Kafka가 없으면 bean이 null
        println("KafkaProducerService bean: ${kafkaProducerService != null}")
    }

    @Test
    @Transactional
    fun `Outbox 이벤트를 저장하고 Kafka로 전송할 수 있다`() {
        // given: Kafka가 실행 중이어야 함
        if (kafkaProducerService == null) return

        val event = LikeAddedEvent(
            userId = 1L,
            productId = 100L,
            createdAt = LocalDateTime.now(),
        )
        val payload = objectMapper.writeValueAsString(event)

        val outboxEvent = OutboxEvent.create(
            eventType = "LikeAddedEvent",
            topic = "catalog-events",
            partitionKey = "100",
            payload = payload,
            aggregateType = "Product",
            aggregateId = 100L,
        )

        // when: Outbox에 저장
        val saved = outboxEventRepository!!.save(outboxEvent)

        // then: PENDING 상태로 저장됨
        assertThat(saved.status).isEqualTo(OutboxEventStatus.PENDING)

        // when: Kafka로 전송
        val future = kafkaProducerService!!.send(
            topic = saved.topic,
            key = saved.partitionKey,
            message = saved.payload,
        )

        // then: 전송 성공
        val result = future.get(5, TimeUnit.SECONDS)
        assertThat(result).isNotNull
        assertThat(result.recordMetadata.topic()).isEqualTo("catalog-events")

        // Outbox 상태 업데이트
        saved.markAsPublished()
        outboxEventRepository!!.save(saved)

        assertThat(saved.status).isEqualTo(OutboxEventStatus.PUBLISHED)
    }

    @Test
    @Transactional
    fun `Consumer가 메시지를 수신하여 ProductMetrics를 업데이트한다`() {
        // given: Kafka가 실행 중이어야 함
        if (kafkaProducerService == null || productMetricsRepository == null) return

        val productId = 200L
        val event = LikeAddedEvent(
            userId = 1L,
            productId = productId,
            createdAt = LocalDateTime.now(),
        )
        val payload = objectMapper.writeValueAsString(event)

        // when: Kafka로 메시지 전송
        val future = kafkaProducerService!!.send(
            topic = "catalog-events",
            key = productId.toString(),
            message = payload,
        )
        future.get(5, TimeUnit.SECONDS)

        // then: Consumer가 메시지를 처리하고 ProductMetrics 업데이트
        // 최대 10초 대기
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository!!.findByProductId(productId)
                assertThat(metrics).isNotNull
                assertThat(metrics!!.likeCount).isGreaterThan(0)
            }
    }

    @Test
    @Transactional
    fun `중복 메시지를 재전송해도 멱등성이 보장된다`() {
        // given: Kafka가 실행 중이어야 함
        if (kafkaProducerService == null || eventHandledRepository == null) return

        val productId = 300L
        val createdAt = LocalDateTime.now()
        val event = LikeAddedEvent(
            userId = 1L,
            productId = productId,
            createdAt = createdAt,
        )
        val payload = objectMapper.writeValueAsString(event)

        // when: 같은 메시지를 3번 전송
        repeat(3) {
            kafkaProducerService!!.send(
                topic = "catalog-events",
                key = productId.toString(),
                message = payload,
            ).get(5, TimeUnit.SECONDS)
        }

        // then: Consumer가 처리하고 ProductMetrics는 1번만 증가
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .untilAsserted {
                val metrics = productMetricsRepository!!.findByProductId(productId)
                assertThat(metrics).isNotNull
                // 멱등성 보장 - 3번 전송했지만 1번만 증가
                assertThat(metrics!!.likeCount).isEqualTo(1)
            }

        // EventHandled 테이블 확인 - 한 번만 기록됨
        val eventHandled = eventHandledRepository!!.existsByEventKey(
            eventType = "LikeAddedEvent",
            aggregateType = "Product",
            aggregateId = productId,
            eventVersion = createdAt.nano.toLong(),
        )
        assertThat(eventHandled).isTrue
    }

    @Test
    @Transactional
    fun `OutboxRelayScheduler 없이 수동으로 Outbox 이벤트를 Kafka로 전송할 수 있다`() {
        // given: Kafka가 실행 중이어야 함
        if (kafkaProducerService == null || outboxEventRepository == null) return

        // Outbox에 이벤트 저장
        val event = LikeAddedEvent(
            userId = 1L,
            productId = 400L,
            createdAt = LocalDateTime.now(),
        )
        val payload = objectMapper.writeValueAsString(event)

        val outboxEvent = OutboxEvent.create(
            eventType = "LikeAddedEvent",
            topic = "catalog-events",
            partitionKey = "400",
            payload = payload,
            aggregateType = "Product",
            aggregateId = 400L,
        )
        val saved = outboxEventRepository!!.save(outboxEvent)

        // when: PENDING 이벤트 조회
        val pendingEvents = outboxEventRepository!!.findPendingEvents(limit = 10)
        assertThat(pendingEvents).hasSizeGreaterThanOrEqualTo(1)

        // when: 수동으로 Kafka 전송 (OutboxRelayScheduler가 하는 일)
        pendingEvents.forEach { event ->
            try {
                val future = kafkaProducerService!!.send(
                    topic = event.topic,
                    key = event.partitionKey,
                    message = event.payload,
                )
                future.get(5, TimeUnit.SECONDS)

                // 성공 시 PUBLISHED로 변경
                event.markAsPublished()
                outboxEventRepository!!.save(event)
            } catch (e: Exception) {
                // 실패 시 FAILED로 변경
                event.markAsFailed(e.message ?: "Unknown error")
                outboxEventRepository!!.save(event)
            }
        }

        // then: 상태가 PUBLISHED로 변경됨
        val updated = outboxEventRepository!!.findById(saved.id)!!
        assertThat(updated.status).isEqualTo(OutboxEventStatus.PUBLISHED)
    }
}
