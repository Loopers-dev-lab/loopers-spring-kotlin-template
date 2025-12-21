package com.loopers.infrastructure.outbox

import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.support.outbox.Outbox
import com.loopers.support.outbox.OutboxCursor
import com.loopers.support.outbox.OutboxCursorRepository
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
import java.time.Duration
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
    private lateinit var properties: OutboxRelayProperties

    @BeforeEach
    fun setUp() {
        mockKafkaTemplate = mockk(relaxed = true)
        properties = OutboxRelayProperties(
            batchSize = 100,
            sendTimeoutSeconds = 5,
            retryIntervalSeconds = 10,
            maxAgeMinutes = 5,
        )

        outboxRelayService = OutboxRelayService(
            kafkaTemplate = mockKafkaTemplate,
            outboxRepository = outboxRepository,
            outboxCursorRepository = outboxCursorRepository,
            outboxFailedRepository = outboxFailedRepository,
            properties = properties,
        )

        clearMocks(mockKafkaTemplate)
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("relay()")
    inner class Relay {

        @Test
        @DisplayName("대기 중인 메시지를 Kafka에 전송하고 성공 결과를 반환한다")
        fun `returns correct RelayResult on success`() {
            // given
            saveOutbox(aggregateId = "1")
            val outbox2 = saveOutbox(aggregateId = "2")
            mockKafkaSendSuccess()

            // when
            val result = outboxRelayService.relay()

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
            outboxRelayService.relay()

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
            outboxRelayService.relay()

            // then
            val cursor = outboxCursorRepository.findLatest()
            assertThat(cursor).isNotNull
            assertThat(cursor!!.lastProcessedId).isEqualTo(outbox2.id)
        }

        @Test
        @DisplayName("첫 번째 메시지 실패 시 재시도 예약 후 처리를 중단한다")
        fun `marks failed message for retry and stops processing`() {
            // given
            val outbox1 = saveOutbox(aggregateId = "1")
            saveOutbox(aggregateId = "2")
            mockKafkaSendFailure(RuntimeException("Kafka send failed"))

            // when
            val result = outboxRelayService.relay()

            // then
            assertThat(result.successCount).isEqualTo(0)
            assertThat(result.failedCount).isEqualTo(0)
            assertThat(result.lastProcessedId).isEqualTo(0L)

            // 커서가 생성되지 않았는지 확인
            val cursor = outboxCursorRepository.findLatest()
            assertThat(cursor).isNull()

            // OutboxFailed에 저장되지 않았는지 확인 (아직 만료되지 않음)
            val failedMessages = outboxFailedRepository.findAll()
            assertThat(failedMessages).isEmpty()

            // 메시지에 nextRetryAt이 설정되었는지 확인
            entityManager.clear()
            val updatedOutbox = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(0L, 10).first()
            assertThat(updatedOutbox.id).isEqualTo(outbox1.id)
            assertThat(updatedOutbox.nextRetryAt).isNotNull()
        }

        @Test
        @DisplayName("메시지가 없으면 현재 커서 ID를 반환한다")
        fun `returns current cursor ID when no messages exist`() {
            // given - no outbox messages
            mockKafkaSendSuccess()

            // when
            val result = outboxRelayService.relay()

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
            val result = outboxRelayService.relay()

            // then
            assertThat(result.successCount).isEqualTo(1)
            assertThat(result.lastProcessedId).isEqualTo(outbox2.id)

            val keySlot = slot<String>()
            verify(exactly = 1) { mockKafkaTemplate.send(any<String>(), capture(keySlot), any<String>()) }
            assertThat(keySlot.captured).isEqualTo("2")
        }

        @Test
        @DisplayName("nextRetryAt이 미래인 첫 번째 메시지에서 처리를 중단한다")
        fun `stops at first message with nextRetryAt in future`() {
            // given
            val outbox1 = saveOutbox(aggregateId = "1")
            // nextRetryAt을 미래로 설정
            outbox1.nextRetryAt = Instant.now().plusSeconds(60)
            outboxRepository.save(outbox1)
            saveOutbox(aggregateId = "2")
            mockKafkaSendSuccess()

            // when
            val result = outboxRelayService.relay()

            // then
            assertThat(result.successCount).isEqualTo(0)
            assertThat(result.failedCount).isEqualTo(0)
            assertThat(result.lastProcessedId).isEqualTo(0L)

            // Kafka 전송이 호출되지 않았는지 확인
            verify(exactly = 0) { mockKafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
        }

        @Test
        @DisplayName("만료된 실패 메시지를 OutboxFailed로 이동하고 계속 진행한다")
        fun `moves expired failed message to OutboxFailed and continues`() {
            // given - 만료 시간(5분)보다 오래된 메시지
            val expiredTime = Instant.now().minus(Duration.ofMinutes(10))
            val outbox1 = saveOutbox(aggregateId = "1", createdAt = expiredTime)
            val outbox2 = saveOutbox(aggregateId = "2")

            // 첫 번째 메시지만 실패
            var callCount = 0
            every { mockKafkaTemplate.send(any<String>(), any<String>(), any<String>()) } answers {
                callCount++
                if (callCount == 1) {
                    createFailedFuture(RuntimeException("Kafka send failed"))
                } else {
                    createSuccessFuture()
                }
            }

            // when
            val result = outboxRelayService.relay()

            // then
            assertThat(result.successCount).isEqualTo(1)
            assertThat(result.failedCount).isEqualTo(1)
            assertThat(result.lastProcessedId).isEqualTo(outbox2.id)

            // OutboxFailed에 저장되었는지 확인
            val failedMessages = outboxFailedRepository.findAll()
            assertThat(failedMessages).hasSize(1)
            assertThat(failedMessages[0].aggregateId).isEqualTo("1")
            assertThat(failedMessages[0].errorMessage).contains("Kafka send failed")
        }

        @Test
        @DisplayName("중간 메시지 실패 시 앞 메시지는 성공 처리하고 실패 지점에서 중단한다")
        fun `processes messages until failure and stops at failed message`() {
            // given
            val outbox1 = saveOutbox(aggregateId = "1")
            val outbox2 = saveOutbox(aggregateId = "2")
            saveOutbox(aggregateId = "3")

            // outbox1: success, outbox2: fail, outbox3: not processed
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
            val result = outboxRelayService.relay()

            // then
            assertThat(result.successCount).isEqualTo(1)
            assertThat(result.failedCount).isEqualTo(0)
            assertThat(result.lastProcessedId).isEqualTo(outbox1.id)

            // OutboxFailed에 저장되지 않음 (만료되지 않음)
            val failedMessages = outboxFailedRepository.findAll()
            assertThat(failedMessages).isEmpty()

            // outbox2에 nextRetryAt이 설정되었는지 확인
            entityManager.clear()
            val messages = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(outbox1.id, 10)
            val updatedOutbox2 = messages.find { it.id == outbox2.id }
            assertThat(updatedOutbox2).isNotNull
            assertThat(updatedOutbox2!!.nextRetryAt).isNotNull()
        }

        @Test
        @DisplayName("nextRetryAt이 과거이면 재시도하여 전송한다")
        fun `retries message when nextRetryAt is in the past`() {
            // given
            val outbox1 = saveOutbox(aggregateId = "1")
            // nextRetryAt을 과거로 설정
            outbox1.nextRetryAt = Instant.now().minusSeconds(60)
            outboxRepository.save(outbox1)
            mockKafkaSendSuccess()

            // when
            val result = outboxRelayService.relay()

            // then
            assertThat(result.successCount).isEqualTo(1)
            assertThat(result.failedCount).isEqualTo(0)
            assertThat(result.lastProcessedId).isEqualTo(outbox1.id)

            // Kafka 전송이 호출되었는지 확인
            verify(exactly = 1) { mockKafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
        }
    }

    // ===========================================
    // Helper methods
    // ===========================================

    private fun saveOutbox(
        eventType: String = "loopers.order.created.v1",
        aggregateType: String = "Order",
        aggregateId: String = UUID.randomUUID().toString(),
        createdAt: Instant = Instant.now(),
    ): Outbox {
        val envelope = CloudEventEnvelope(
            id = UUID.randomUUID().toString(),
            type = eventType,
            source = "commerce-api",
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            time = createdAt,
            payload = """{"test": "data"}""",
        )
        return outboxRepository.save(Outbox.from(envelope))
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
}
