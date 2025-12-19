package com.loopers.infrastructure.outbox

import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.support.outbox.Outbox
import com.loopers.support.outbox.OutboxCursor
import com.loopers.support.outbox.OutboxCursorRepository
import com.loopers.support.outbox.OutboxFailed
import com.loopers.support.outbox.OutboxFailedRepository
import com.loopers.support.outbox.OutboxRepository
import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManager
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

@SpringBootTest
@DisplayName("OutboxRelayer 통합 테스트")
class OutboxRelayerIntegrationTest @Autowired constructor(
    private val outboxRepository: OutboxRepository,
    private val outboxCursorRepository: OutboxCursorRepository,
    private val outboxFailedRepository: OutboxFailedRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val entityManager: EntityManager,
) {
    private lateinit var mockKafkaTemplate: KafkaTemplate<String, String>
    private lateinit var outboxRelayer: OutboxRelayer
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry
    private lateinit var circuitBreaker: CircuitBreaker

    @BeforeEach
    fun setUp() {
        mockKafkaTemplate = mockk(relaxed = true)

        // Create a new circuit breaker registry for each test
        circuitBreakerRegistry = CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50f)
                .build(),
        )
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("outbox-relay")

        // Create OutboxRelayer with mocked dependencies
        outboxRelayer = OutboxRelayer(
            stringKafkaTemplate = mockKafkaTemplate,
            outboxRepository = outboxRepository,
            outboxCursorRepository = outboxCursorRepository,
            outboxFailedRepository = outboxFailedRepository,
            circuitBreakerRegistry = circuitBreakerRegistry,
        )

        circuitBreaker.transitionToClosedState()
        clearMocks(mockKafkaTemplate)
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("relayNewMessages()")
    inner class RelayNewMessages {

        @Test
        @DisplayName("대기 중인 메시지를 올바른 topic과 key로 Kafka에 전송한다")
        fun `sends pending messages to Kafka with correct topic and key`() {
            // given
            saveOutbox(
                eventType = "loopers.order.created.v1",
                aggregateType = "Order",
                aggregateId = "123",
            )
            mockKafkaSendSuccess()

            // when
            outboxRelayer.relayNewMessages()

            // then
            val topicSlot = slot<String>()
            val keySlot = slot<String>()
            verify { mockKafkaTemplate.send(capture(topicSlot), capture(keySlot), any()) }

            assertThat(topicSlot.captured).isEqualTo("order-events")
            assertThat(keySlot.captured).isEqualTo("123")
        }

        @Test
        @DisplayName("처리 후 커서를 갱신한다")
        fun `updates cursor after processing`() {
            // given
            saveOutbox(aggregateId = "1")
            val outbox2 = saveOutbox(aggregateId = "2")
            mockKafkaSendSuccess()

            // when
            outboxRelayer.relayNewMessages()

            // then
            val cursor = outboxCursorRepository.findLatest()
            assertThat(cursor).isNotNull
            assertThat(cursor!!.lastProcessedId).isEqualTo(outbox2.id)
        }

        @Test
        @DisplayName("전송 실패한 메시지를 OutboxFailed로 이동한다")
        fun `moves failed messages to OutboxFailed`() {
            // given
            val outbox = saveOutbox(aggregateId = "123")
            mockKafkaSendFailure(RuntimeException("Kafka send failed"))

            // when
            outboxRelayer.relayNewMessages()

            // then - Use direct query to find all OutboxFailed records
            val failedMessages = findAllOutboxFailed()
            assertThat(failedMessages).hasSize(1)
            assertThat(failedMessages[0].eventId).isEqualTo(outbox.eventId)
            assertThat(failedMessages[0].lastError).contains("Kafka send failed")
        }

        @Test
        @DisplayName("서킷브레이커가 OPEN이면 스킵한다")
        fun `skips when circuit breaker is OPEN`() {
            // given
            saveOutbox(aggregateId = "123")
            circuitBreaker.transitionToOpenState()

            // when
            outboxRelayer.relayNewMessages()

            // then
            verify(exactly = 0) { mockKafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
            val cursor = outboxCursorRepository.findLatest()
            assertThat(cursor).isNull()
        }

        @Test
        @DisplayName("메시지가 없으면 아무 작업도 하지 않는다")
        fun `does nothing when no messages exist`() {
            // given - no outbox messages
            mockKafkaSendSuccess()

            // when
            outboxRelayer.relayNewMessages()

            // then
            verify(exactly = 0) { mockKafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
        }

        @Test
        @DisplayName("커서 이후의 메시지만 처리한다")
        fun `processes only messages after cursor`() {
            // given
            val outbox1 = saveOutbox(aggregateId = "1")
            outboxCursorRepository.save(OutboxCursor.create(outbox1.id))
            saveOutbox(aggregateId = "2")
            mockKafkaSendSuccess()

            // when
            outboxRelayer.relayNewMessages()

            // then
            val keySlot = slot<String>()
            verify(exactly = 1) { mockKafkaTemplate.send(any<String>(), capture(keySlot), any<String>()) }
            assertThat(keySlot.captured).isEqualTo("2")
        }

        @Test
        @DisplayName("부분 성공 시 성공한 메시지까지 커서를 갱신하고 실패 메시지는 OutboxFailed로 이동한다")
        fun `handles partial success correctly`() {
            // given
            saveOutbox(aggregateId = "1")
            saveOutbox(aggregateId = "2")
            val outbox3 = saveOutbox(aggregateId = "3")

            // outbox1: success, outbox2: fail, outbox3: success
            var callCount = 0
            every { mockKafkaTemplate.send(any<String>(), any<String>(), any<String>()) } answers {
                callCount++
                if (callCount == 2) {
                    createFailedFuture(RuntimeException("Failed on second message"))
                } else {
                    createSuccessFuture()
                }
            }

            // when
            outboxRelayer.relayNewMessages()

            // then
            val cursor = outboxCursorRepository.findLatest()
            assertThat(cursor!!.lastProcessedId).isEqualTo(outbox3.id)

            // Use direct query to find all OutboxFailed records
            val failedMessages = findAllOutboxFailed()
            assertThat(failedMessages).hasSize(1)
            assertThat(failedMessages[0].aggregateId).isEqualTo("2")
        }
    }

    @Nested
    @DisplayName("retryFailedMessages()")
    inner class RetryFailedMessages {

        @Test
        @DisplayName("재시도 가능한 메시지를 재전송한다")
        fun `resends retryable messages`() {
            // given
            saveOutboxFailed(
                eventType = "loopers.payment.paid.v1",
                aggregateId = "456",
            )
            mockKafkaSendSuccess()

            // when
            outboxRelayer.retryFailedMessages()

            // then
            val topicSlot = slot<String>()
            val keySlot = slot<String>()
            verify { mockKafkaTemplate.send(capture(topicSlot), capture(keySlot), any()) }

            assertThat(topicSlot.captured).isEqualTo("payment-events")
            assertThat(keySlot.captured).isEqualTo("456")
        }

        @Test
        @DisplayName("재전송 성공 시 OutboxFailed에서 삭제한다")
        fun `deletes successfully sent messages from OutboxFailed`() {
            // given
            saveOutboxFailed(aggregateId = "123")
            mockKafkaSendSuccess()

            // when
            outboxRelayer.retryFailedMessages()

            // then
            val remainingFailed = outboxFailedRepository.findRetryable(100)
            assertThat(remainingFailed).isEmpty()
        }

        @Test
        @DisplayName("재전송 실패 시 retryCount를 증가시킨다")
        fun `increments retryCount on failure`() {
            // given
            val failed = saveOutboxFailed(aggregateId = "123")
            val initialRetryCount = failed.retryCount
            mockKafkaSendFailure(RuntimeException("Retry failed"))

            // when
            outboxRelayer.retryFailedMessages()

            // then - After failure, nextRetryAt is set to future, so use direct query
            val allFailed = findAllOutboxFailed()
            assertThat(allFailed).hasSize(1)
            assertThat(allFailed[0].retryCount).isEqualTo(initialRetryCount + 1)
            assertThat(allFailed[0].lastError).contains("Retry failed")
        }

        @Test
        @DisplayName("서킷브레이커가 OPEN이면 스킵한다")
        fun `skips when circuit breaker is OPEN`() {
            // given
            saveOutboxFailed(aggregateId = "123")
            circuitBreaker.transitionToOpenState()

            // when
            outboxRelayer.retryFailedMessages()

            // then
            verify(exactly = 0) { mockKafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
        }

        @Test
        @DisplayName("재시도 가능한 메시지가 없으면 아무 작업도 하지 않는다")
        fun `does nothing when no retryable messages exist`() {
            // given - no failed messages
            mockKafkaSendSuccess()

            // when
            outboxRelayer.retryFailedMessages()

            // then
            verify(exactly = 0) { mockKafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
        }
    }

    // ===========================================
    // Helper methods
    // ===========================================

    private fun saveOutbox(
        eventType: String = "loopers.order.created.v1",
        aggregateType: String = "Order",
        aggregateId: String = UUID.randomUUID().toString(),
    ): Outbox {
        val envelope = CloudEventEnvelope(
            id = UUID.randomUUID().toString(),
            type = eventType,
            source = "commerce-api",
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            time = Instant.now(),
            payload = """{"test": "data"}""",
        )
        return outboxRepository.save(Outbox.from(envelope))
    }

    private fun saveOutboxFailed(
        eventType: String = "loopers.order.created.v1",
        aggregateType: String = "Order",
        aggregateId: String = UUID.randomUUID().toString(),
    ): OutboxFailed {
        val outbox = Outbox(
            id = 0,
            eventId = UUID.randomUUID().toString(),
            eventType = eventType,
            source = "commerce-api",
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            payload = """{"test": "data"}""",
            createdAt = Instant.now(),
        )
        val failed = OutboxFailed.from(outbox, "Initial error")
        // Set nextRetryAt to past to make it retryable
        failed.nextRetryAt = Instant.now().minusSeconds(60)
        return outboxFailedRepository.save(failed)
    }

    private fun mockKafkaSendSuccess() {
        every { mockKafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns createSuccessFuture()
    }

    private fun mockKafkaSendFailure(exception: Exception) {
        every { mockKafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns createFailedFuture(exception)
    }

    private fun createSuccessFuture(): CompletableFuture<SendResult<String, String>> {
        val topicPartition = TopicPartition("test-topic", 0)
        val recordMetadata = RecordMetadata(topicPartition, 0, 0, 0L, 0, 0)
        val producerRecord = ProducerRecord<String, String>("test-topic", "key", "value")
        val sendResult = SendResult(producerRecord, recordMetadata)
        return CompletableFuture.completedFuture(sendResult)
    }

    private fun createFailedFuture(exception: Exception): CompletableFuture<SendResult<String, String>> {
        val future = CompletableFuture<SendResult<String, String>>()
        future.completeExceptionally(exception)
        return future
    }

    @Suppress("UNCHECKED_CAST")
    private fun findAllOutboxFailed(): List<OutboxFailed> {
        return entityManager
            .createQuery("SELECT f FROM OutboxFailed f ORDER BY f.id")
            .resultList as List<OutboxFailed>
    }
}
