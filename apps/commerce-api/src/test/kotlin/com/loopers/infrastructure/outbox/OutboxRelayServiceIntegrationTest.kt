package com.loopers.infrastructure.outbox

import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.support.outbox.Outbox
import com.loopers.support.outbox.OutboxCursor
import com.loopers.support.outbox.OutboxCursorRepository
import com.loopers.support.outbox.OutboxFailed
import com.loopers.support.outbox.OutboxFailedRepository
import com.loopers.support.outbox.OutboxRepository
import com.loopers.utils.DatabaseCleanUp
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityManager
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
@DisplayName("OutboxRelayService 통합 테스트")
class OutboxRelayServiceIntegrationTest @Autowired constructor(
    private val outboxRepository: OutboxRepository,
    private val outboxCursorRepository: OutboxCursorRepository,
    private val outboxFailedRepository: OutboxFailedRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val entityManager: EntityManager,
) {
    private lateinit var mockKafkaTemplate: KafkaTemplate<String, String>
    private lateinit var outboxRelayService: OutboxRelayService

    @BeforeEach
    fun setUp() {
        mockKafkaTemplate = mockk(relaxed = true)

        outboxRelayService = OutboxRelayService(
            kafkaTemplate = mockKafkaTemplate,
            outboxRepository = outboxRepository,
            outboxCursorRepository = outboxCursorRepository,
            outboxFailedRepository = outboxFailedRepository,
        )

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
        @DisplayName("대기 중인 메시지를 Kafka에 전송하고 성공 결과를 반환한다")
        fun `returns correct RelayResult on success`() {
            // given
            saveOutbox(aggregateId = "1")
            val outbox2 = saveOutbox(aggregateId = "2")
            mockKafkaSendSuccess()

            // when
            val result = outboxRelayService.relayNewMessages()

            // then
            assertThat(result.successCount).isEqualTo(2)
            assertThat(result.failedCount).isEqualTo(0)
            assertThat(result.lastProcessedId).isEqualTo(outbox2.id)
        }

        @Test
        @DisplayName("올바른 topic과 key로 Kafka에 전송한다")
        fun `sends messages to Kafka with correct topic and key`() {
            // given
            saveOutbox(
                eventType = "loopers.order.created.v1",
                aggregateType = "Order",
                aggregateId = "123",
            )
            mockKafkaSendSuccess()

            // when
            outboxRelayService.relayNewMessages()

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
            outboxRelayService.relayNewMessages()

            // then
            val cursor = outboxCursorRepository.findLatest()
            assertThat(cursor).isNotNull
            assertThat(cursor!!.lastProcessedId).isEqualTo(outbox2.id)
        }

        @Test
        @DisplayName("부분 실패 시 실패한 메시지를 OutboxFailed로 이동한다")
        fun `saves failed messages to OutboxFailed on partial failure`() {
            // given
            saveOutbox(aggregateId = "1")
            val outbox2 = saveOutbox(aggregateId = "2")

            // outbox1: success, outbox2: fail
            var callCount = 0
            every { mockKafkaTemplate.send(any<String>(), any<String>(), any<String>()) } answers {
                callCount++
                if (callCount == 2) {
                    createFailedFuture(RuntimeException("Kafka send failed"))
                } else {
                    createSuccessFuture()
                }
            }

            // when
            val result = outboxRelayService.relayNewMessages()

            // then
            assertThat(result.successCount).isEqualTo(1)
            assertThat(result.failedCount).isEqualTo(1)
            assertThat(result.lastProcessedId).isEqualTo(outbox2.id)

            val failedMessages = findAllOutboxFailed()
            assertThat(failedMessages).hasSize(1)
            assertThat(failedMessages[0].aggregateId).isEqualTo("2")
            assertThat(failedMessages[0].lastError).contains("Kafka send failed")
        }

        @Test
        @DisplayName("전부 실패 시 커서를 이동하지 않고 OutboxFailed에 저장하지 않는다")
        fun `does not update cursor and does not save to OutboxFailed when all messages fail`() {
            // given
            val initialCursor = outboxCursorRepository.save(OutboxCursor.create(100L))
            saveOutbox(aggregateId = "1")
            saveOutbox(aggregateId = "2")
            mockKafkaSendFailure(RuntimeException("Kafka is down"))

            // when
            val result = outboxRelayService.relayNewMessages()

            // then
            assertThat(result.successCount).isEqualTo(0)
            assertThat(result.failedCount).isEqualTo(2)
            assertThat(result.lastProcessedId).isEqualTo(initialCursor.lastProcessedId)

            // 커서가 이동하지 않았는지 확인
            val cursor = outboxCursorRepository.findLatest()
            assertThat(cursor!!.lastProcessedId).isEqualTo(initialCursor.lastProcessedId)

            // OutboxFailed에 저장되지 않았는지 확인
            val failedMessages = findAllOutboxFailed()
            assertThat(failedMessages).isEmpty()
        }

        @Test
        @DisplayName("메시지가 없으면 현재 커서 ID를 반환한다")
        fun `returns current cursor ID when no messages exist`() {
            // given - no outbox messages
            mockKafkaSendSuccess()

            // when
            val result = outboxRelayService.relayNewMessages()

            // then
            assertThat(result.successCount).isEqualTo(0)
            assertThat(result.failedCount).isEqualTo(0)
            assertThat(result.lastProcessedId).isEqualTo(0L)
            verify(exactly = 0) { mockKafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
        }

        @Test
        @DisplayName("커서 이후의 메시지만 처리한다")
        fun `processes only messages after cursor`() {
            // given
            val outbox1 = saveOutbox(aggregateId = "1")
            outboxCursorRepository.save(OutboxCursor.create(outbox1.id))
            val outbox2 = saveOutbox(aggregateId = "2")
            mockKafkaSendSuccess()

            // when
            val result = outboxRelayService.relayNewMessages()

            // then
            assertThat(result.successCount).isEqualTo(1)
            assertThat(result.lastProcessedId).isEqualTo(outbox2.id)

            val keySlot = slot<String>()
            verify(exactly = 1) { mockKafkaTemplate.send(any<String>(), capture(keySlot), any<String>()) }
            assertThat(keySlot.captured).isEqualTo("2")
        }

        @Test
        @DisplayName("부분 성공 시 정확한 결과를 반환한다")
        fun `returns correct counts on partial success`() {
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
            val result = outboxRelayService.relayNewMessages()

            // then
            assertThat(result.successCount).isEqualTo(2)
            assertThat(result.failedCount).isEqualTo(1)
            assertThat(result.lastProcessedId).isEqualTo(outbox3.id)

            val failedMessages = findAllOutboxFailed()
            assertThat(failedMessages).hasSize(1)
            assertThat(failedMessages[0].aggregateId).isEqualTo("2")
        }
    }

    @Nested
    @DisplayName("retryFailedMessages()")
    inner class RetryFailedMessages {

        @Test
        @DisplayName("재시도 가능한 메시지를 재전송하고 성공 결과를 반환한다")
        fun `returns correct RetryResult on success`() {
            // given
            saveOutboxFailed(aggregateId = "1")
            saveOutboxFailed(aggregateId = "2")
            mockKafkaSendSuccess()

            // when
            val result = outboxRelayService.retryFailedMessages()

            // then
            assertThat(result.successCount).isEqualTo(2)
            assertThat(result.failedCount).isEqualTo(0)
        }

        @Test
        @DisplayName("재전송 성공 시 OutboxFailed에서 삭제한다")
        fun `deletes successfully sent messages from OutboxFailed`() {
            // given
            saveOutboxFailed(aggregateId = "123")
            mockKafkaSendSuccess()

            // when
            val result = outboxRelayService.retryFailedMessages()

            // then
            assertThat(result.successCount).isEqualTo(1)
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
            val result = outboxRelayService.retryFailedMessages()

            // then
            assertThat(result.successCount).isEqualTo(0)
            assertThat(result.failedCount).isEqualTo(1)

            val allFailed = findAllOutboxFailed()
            assertThat(allFailed).hasSize(1)
            assertThat(allFailed[0].retryCount).isEqualTo(initialRetryCount + 1)
            assertThat(allFailed[0].lastError).contains("Retry failed")
        }

        @Test
        @DisplayName("재시도 가능한 메시지가 없으면 카운트 0을 반환한다")
        fun `returns zero counts when no retryable messages exist`() {
            // given - no failed messages
            mockKafkaSendSuccess()

            // when
            val result = outboxRelayService.retryFailedMessages()

            // then
            assertThat(result.successCount).isEqualTo(0)
            assertThat(result.failedCount).isEqualTo(0)
            verify(exactly = 0) { mockKafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
        }

        @Test
        @DisplayName("올바른 topic과 key로 Kafka에 재전송한다")
        fun `sends messages to Kafka with correct topic and key`() {
            // given
            saveOutboxFailed(
                eventType = "loopers.payment.paid.v1",
                aggregateId = "456",
            )
            mockKafkaSendSuccess()

            // when
            outboxRelayService.retryFailedMessages()

            // then
            val topicSlot = slot<String>()
            val keySlot = slot<String>()
            verify { mockKafkaTemplate.send(capture(topicSlot), capture(keySlot), any()) }

            assertThat(topicSlot.captured).isEqualTo("payment-events")
            assertThat(keySlot.captured).isEqualTo("456")
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
