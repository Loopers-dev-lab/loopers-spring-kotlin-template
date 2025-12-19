package com.loopers.infrastructure.event

import com.loopers.domain.event.EventOutbox
import com.loopers.utils.DatabaseCleanUp
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * OutboxEventPublisher 테스트
 *
 * 테스트 범위:
 * - 미처리 이벤트 Kafka 발행
 * - 발행 성공 시 processed = true 업데이트
 * - 발행 실패 시 재시도 로직
 * - 최대 재시도 초과 시 DLQ 이동
 * - aggregateType별 토픽 라우팅
 */
@SpringBootTest
@TestPropertySource(properties = [
    "spring.kafka.enabled=false",
    "spring.task.scheduling.enabled=false"
])
class OutboxEventPublisherTest @Autowired constructor(
    private val eventOutboxRepository: EventOutboxJpaRepository,
    private val deadLetterQueueRepository: DeadLetterQueueRepository,
    private val deadLetterQueueService: DeadLetterQueueService,
    private val databaseCleanUp: DatabaseCleanUp
) {

    private lateinit var kafkaTemplate: KafkaTemplate<String, String>
    private lateinit var outboxEventPublisher: OutboxEventPublisher

    @BeforeEach
    fun setUp() {
        kafkaTemplate = mockk(relaxed = true)
        outboxEventPublisher = OutboxEventPublisher(
            eventOutboxRepository,
            kafkaTemplate,
            deadLetterQueueService
        )
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        clearAllMocks()
    }

    @DisplayName("미처리 이벤트를 Kafka로 발행하고 processed = true로 업데이트한다")
    @Test
    fun publishPendingEventsSuccessfully() {
        // given
        val outbox = EventOutbox(
            eventId = "test-event-1",
            eventType = "PRODUCT_LIKED",
            aggregateType = "product",
            aggregateId = 100L,
            payload = """{"eventId":"test-event-1","productId":100}""",
            occurredAt = Instant.now()
        )
        eventOutboxRepository.save(outbox)

        // Kafka 발행 성공 Mock
        val sendResult = mockk<SendResult<String, String>>(relaxed = true)
        every { sendResult.recordMetadata.partition() } returns 0
        every { sendResult.recordMetadata.offset() } returns 12345L

        val future = CompletableFuture<SendResult<String, String>>()
        future.complete(sendResult)
        every { kafkaTemplate.send(any(), any(), any()) } returns future

        // when
        outboxEventPublisher.publishPendingEvents()

        // then
        verify(exactly = 1) {
            kafkaTemplate.send("catalog-events", "100", any())
        }

        val updated = eventOutboxRepository.findById(outbox.id!!).get()
        assertThat(updated.processed).isTrue()
        assertThat(updated.processedAt).isNotNull()
        assertThat(updated.kafkaPartition).isEqualTo(0)
        assertThat(updated.kafkaOffset).isEqualTo(12345L)
    }

    @DisplayName("ORDER aggregateType은 order-events 토픽으로 발행된다")
    @Test
    fun publishOrderEventsToCorrectTopic() {
        // given
        val outbox = EventOutbox(
            eventId = "test-event-2",
            eventType = "ORDER_CREATED",
            aggregateType = "order",
            aggregateId = 1000L,
            payload = """{"eventId":"test-event-2","orderId":1000}""",
            occurredAt = Instant.now()
        )
        eventOutboxRepository.save(outbox)

        val sendResult = mockk<SendResult<String, String>>(relaxed = true)
        every { sendResult.recordMetadata.partition() } returns 1
        every { sendResult.recordMetadata.offset() } returns 99999L

        val future = CompletableFuture<SendResult<String, String>>()
        future.complete(sendResult)
        every { kafkaTemplate.send(any(), any(), any()) } returns future

        // when
        outboxEventPublisher.publishPendingEvents()

        // then
        verify(exactly = 1) {
            kafkaTemplate.send("order-events", "1000", any())
        }
    }

    @DisplayName("Kafka 발행 실패 시 retryCount가 증가하고 lastError가 기록된다")
    @Test
    fun increaseRetryCountOnFailure() {
        // given
        val outbox = EventOutbox(
            eventId = "test-event-3",
            eventType = "PRODUCT_LIKED",
            aggregateType = "product",
            aggregateId = 200L,
            payload = """{"eventId":"test-event-3"}""",
            occurredAt = Instant.now()
        )
        eventOutboxRepository.save(outbox)

        // Kafka 발행 실패 Mock
        val future = CompletableFuture<SendResult<String, String>>()
        future.completeExceptionally(RuntimeException("Kafka connection failed"))
        every { kafkaTemplate.send(any(), any(), any()) } returns future

        // when
        outboxEventPublisher.publishPendingEvents()

        // then
        val updated = eventOutboxRepository.findById(outbox.id!!).get()
        assertThat(updated.processed).isFalse()
        assertThat(updated.retryCount).isEqualTo(1)
        assertThat(updated.lastError).contains("Kafka connection failed")
    }

    @DisplayName("최대 재시도 횟수 초과 시 DLQ로 이동하고 processed = true로 변경된다")
    @Test
    fun moveToDlqAfterMaxRetries() {
        // given
        val outbox = EventOutbox(
            eventId = "test-event-4",
            eventType = "PRODUCT_LIKED",
            aggregateType = "product",
            aggregateId = 300L,
            payload = """{"eventId":"test-event-4"}""",
            occurredAt = Instant.now(),
            retryCount = 2 // 이미 2번 실패
        )
        eventOutboxRepository.save(outbox)

        // Kafka 발행 실패 Mock (3번째 실패)
        val future = CompletableFuture<SendResult<String, String>>()
        future.completeExceptionally(RuntimeException("Kafka still failing"))
        every { kafkaTemplate.send(any(), any(), any()) } returns future

        // when
        outboxEventPublisher.publishPendingEvents()

        // then: Outbox는 processed = true
        val updated = eventOutboxRepository.findById(outbox.id!!).get()
        assertThat(updated.processed).isTrue()
        assertThat(updated.retryCount).isEqualTo(3)

        // then: DLQ에 저장됨
        val dlqList = deadLetterQueueRepository.findAll()
        assertThat(dlqList).hasSize(1)
        assertThat(dlqList[0].eventId).isEqualTo("test-event-4")
        assertThat(dlqList[0].eventType).isEqualTo("PRODUCT_LIKED")
        assertThat(dlqList[0].processed).isFalse()
        assertThat(dlqList[0].originalRetryCount).isEqualTo(3)
    }

    @DisplayName("여러 미처리 이벤트를 한 번에 발행한다")
    @Test
    fun publishMultiplePendingEvents() {
        // given
        val outbox1 = EventOutbox(
            eventId = "test-event-5",
            eventType = "PRODUCT_LIKED",
            aggregateType = "product",
            aggregateId = 100L,
            payload = """{"eventId":"test-event-5"}""",
            occurredAt = Instant.now()
        )
        val outbox2 = EventOutbox(
            eventId = "test-event-6",
            eventType = "ORDER_CREATED",
            aggregateType = "order",
            aggregateId = 1000L,
            payload = """{"eventId":"test-event-6"}""",
            occurredAt = Instant.now()
        )
        eventOutboxRepository.saveAll(listOf(outbox1, outbox2))

        val sendResult = mockk<SendResult<String, String>>(relaxed = true)
        every { sendResult.recordMetadata.partition() } returns 0
        every { sendResult.recordMetadata.offset() } returns 1L

        val future = CompletableFuture<SendResult<String, String>>()
        future.complete(sendResult)
        every { kafkaTemplate.send(any(), any(), any()) } returns future

        // when
        outboxEventPublisher.publishPendingEvents()

        // then: 2개 모두 발행됨
        verify(exactly = 1) { kafkaTemplate.send("catalog-events", "100", any()) }
        verify(exactly = 1) { kafkaTemplate.send("order-events", "1000", any()) }

        val allOutbox = eventOutboxRepository.findAll()
        assertThat(allOutbox).hasSize(2)
        assertThat(allOutbox.all { it.processed }).isTrue()
    }

    @DisplayName("aggregateId를 partitionKey로 사용하여 순서 보장한다")
    @Test
    fun useAggregateIdAsPartitionKey() {
        // given
        val aggregateId = 999L
        val outbox = EventOutbox(
            eventId = "test-event-7",
            eventType = "PRODUCT_LIKED",
            aggregateType = "product",
            aggregateId = aggregateId,
            payload = """{"eventId":"test-event-7"}""",
            occurredAt = Instant.now()
        )
        eventOutboxRepository.save(outbox)

        val sendResult = mockk<SendResult<String, String>>(relaxed = true)
        every { sendResult.recordMetadata.partition() } returns 0
        every { sendResult.recordMetadata.offset() } returns 1L

        val future = CompletableFuture<SendResult<String, String>>()
        future.complete(sendResult)
        every { kafkaTemplate.send(any(), any(), any()) } returns future

        // when
        outboxEventPublisher.publishPendingEvents()

        // then: partitionKey = aggregateId.toString()
        verify(exactly = 1) {
            kafkaTemplate.send(any(), "999", any())
        }
    }

    @DisplayName("이미 처리된 이벤트는 재발행하지 않는다")
    @Test
    fun skipAlreadyProcessedEvents() {
        // given
        val outbox = EventOutbox(
            eventId = "test-event-8",
            eventType = "PRODUCT_LIKED",
            aggregateType = "product",
            aggregateId = 100L,
            payload = """{"eventId":"test-event-8"}""",
            occurredAt = Instant.now(),
            processed = true,
            processedAt = Instant.now()
        )
        eventOutboxRepository.save(outbox)

        // when
        outboxEventPublisher.publishPendingEvents()

        // then: Kafka 발행 호출 안됨
        verify(exactly = 0) { kafkaTemplate.send(any(), any(), any()) }
    }
}
