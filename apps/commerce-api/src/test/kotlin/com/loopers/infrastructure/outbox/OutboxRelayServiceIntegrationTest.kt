package com.loopers.infrastructure.outbox

import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.support.outbox.Outbox
import com.loopers.support.outbox.OutboxCursor
import com.loopers.support.outbox.OutboxCursorRepository
import com.loopers.support.outbox.OutboxFailedRepository
import com.loopers.support.outbox.OutboxRepository
import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * OutboxRelayService 통합 테스트
 *
 * Testcontainer Kafka를 사용하여 실제 메시지 전송을 검증합니다.
 * 실패 시나리오는 SpyBean을 통해 제어합니다.
 *
 * 검증 범위:
 * - Outbox 메시지 처리 및 Kafka 전송 (상태 검증: RelayResult, 커서, 실패 테이블)
 * - 재시도 로직 (nextRetryAt 설정)
 * - 만료된 메시지의 실패 테이블 이동
 */
@SpringBootTest
@DisplayName("OutboxRelayService 통합 테스트")
class OutboxRelayServiceIntegrationTest @Autowired constructor(
    private val outboxRepository: OutboxRepository,
    private val outboxCursorRepository: OutboxCursorRepository,
    private val outboxFailedRepository: OutboxFailedRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val entityManager: EntityManager,
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {
    @MockitoSpyBean
    private lateinit var spyKafkaTemplate: KafkaTemplate<String, String>

    private lateinit var outboxRelayService: OutboxRelayService
    private lateinit var properties: OutboxRelayProperties

    @BeforeEach
    fun setUp() {
        properties = OutboxRelayProperties(
            batchSize = 100,
            sendTimeoutSeconds = 5,
            retryIntervalSeconds = 10,
            maxAgeMinutes = 5,
        )

        outboxRelayService = OutboxRelayService(
            kafkaTemplate = spyKafkaTemplate,
            outboxRepository = outboxRepository,
            outboxCursorRepository = outboxCursorRepository,
            outboxFailedRepository = outboxFailedRepository,
            properties = properties,
        )
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        org.mockito.Mockito.reset(spyKafkaTemplate)
    }

    @Nested
    @DisplayName("relay() - 성공 시나리오")
    inner class RelaySuccess {

        @Test
        @DisplayName("대기 중인 메시지를 Kafka에 전송하고 성공 결과를 반환한다")
        fun `returns correct RelayResult on success`() {
            // given
            saveOutbox(aggregateId = "1")
            val outbox2 = saveOutbox(aggregateId = "2")

            // when - 실제 Kafka로 전송
            val result = outboxRelayService.relay()

            // then - 상태 검증
            assertThat(result.successCount).isEqualTo(2)
            assertThat(result.failedCount).isEqualTo(0)
            assertThat(result.lastProcessedId).isEqualTo(outbox2.id)
        }

        @Test
        @DisplayName("처리 후 커서를 갱신한다")
        fun `updates cursor after processing`() {
            // given
            saveOutbox(aggregateId = "1")
            val outbox2 = saveOutbox(aggregateId = "2")

            // when - 실제 Kafka로 전송
            outboxRelayService.relay()

            // then - 커서 상태 검증
            val cursor = outboxCursorRepository.findLatest()
            assertThat(cursor).isNotNull
            assertThat(cursor!!.lastProcessedId).isEqualTo(outbox2.id)
        }

        @Test
        @DisplayName("메시지가 없으면 현재 커서 ID를 반환한다")
        fun `returns current cursor ID when no messages exist`() {
            // given - no outbox messages

            // when
            val result = outboxRelayService.relay()

            // then
            assertThat(result.successCount).isEqualTo(0)
            assertThat(result.failedCount).isEqualTo(0)
            assertThat(result.lastProcessedId).isEqualTo(0L)
        }

        @Test
        @DisplayName("커서 이후의 메시지만 처리한다")
        fun `processes only messages after cursor`() {
            // given
            val outbox1 = saveOutbox(aggregateId = "1")
            outboxCursorRepository.save(OutboxCursor.create(outbox1.id))
            val outbox2 = saveOutbox(aggregateId = "2")

            // when
            val result = outboxRelayService.relay()

            // then
            assertThat(result.successCount).isEqualTo(1)
            assertThat(result.lastProcessedId).isEqualTo(outbox2.id)
        }

        @Test
        @DisplayName("nextRetryAt이 과거이면 재시도하여 전송한다")
        fun `retries message when nextRetryAt is in the past`() {
            // given
            val outbox1 = saveOutbox(aggregateId = "1")
            outbox1.nextRetryAt = Instant.now().minusSeconds(60)
            outboxRepository.save(outbox1)

            // when
            val result = outboxRelayService.relay()

            // then
            assertThat(result.successCount).isEqualTo(1)
            assertThat(result.failedCount).isEqualTo(0)
            assertThat(result.lastProcessedId).isEqualTo(outbox1.id)
        }
    }

    @Nested
    @DisplayName("relay() - 차단/재시도 시나리오")
    inner class RelayBlocking {

        @Test
        @DisplayName("nextRetryAt이 미래인 첫 번째 메시지에서 처리를 중단한다")
        fun `stops at first message with nextRetryAt in future`() {
            // given
            val outbox1 = saveOutbox(aggregateId = "1")
            outbox1.nextRetryAt = Instant.now().plusSeconds(60)
            outboxRepository.save(outbox1)
            saveOutbox(aggregateId = "2")

            // when - nextRetryAt이 미래이므로 Kafka 호출 없이 중단
            val result = outboxRelayService.relay()

            // then
            assertThat(result.successCount).isEqualTo(0)
            assertThat(result.failedCount).isEqualTo(0)
            assertThat(result.lastProcessedId).isEqualTo(0L)
        }
    }

    @Nested
    @DisplayName("relay() - 실패 시나리오")
    inner class RelayFailure {

        @Test
        @DisplayName("첫 번째 메시지 실패 시 재시도 예약 후 처리를 중단한다")
        fun `marks failed message for retry and stops processing`() {
            // given
            val outbox1 = saveOutbox(aggregateId = "1")
            saveOutbox(aggregateId = "2")

            // Kafka 전송 실패를 시뮬레이션
            val failedFuture = CompletableFuture<Any>()
            failedFuture.completeExceptionally(RuntimeException("Kafka send failed"))
            org.mockito.Mockito.doReturn(failedFuture)
                .`when`(spyKafkaTemplate).send(
                    org.mockito.kotlin.any<String>(),
                    org.mockito.kotlin.any<String>(),
                    org.mockito.kotlin.any<String>(),
                )

            // when
            val result = outboxRelayService.relay()

            // then - 상태 검증
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
        @DisplayName("만료된 실패 메시지를 OutboxFailed로 이동하고 계속 진행한다")
        fun `moves expired failed message to OutboxFailed and continues`() {
            // given - 만료 시간(5분)보다 오래된 메시지
            val expiredTime = Instant.now().minus(Duration.ofMinutes(10))
            saveOutbox(aggregateId = "1", createdAt = expiredTime)
            val outbox2 = saveOutbox(aggregateId = "2")

            // 첫 번째 호출만 실패, 두 번째는 성공
            val failedFuture = CompletableFuture<Any>()
            failedFuture.completeExceptionally(RuntimeException("Kafka send failed"))

            org.mockito.Mockito.doReturn(failedFuture)
                .doCallRealMethod()
                .`when`(spyKafkaTemplate).send(
                    org.mockito.kotlin.any<String>(),
                    org.mockito.kotlin.any<String>(),
                    org.mockito.kotlin.any<String>(),
                )

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

            // 두 번째 호출만 실패
            val failedFuture = CompletableFuture<Any>()
            failedFuture.completeExceptionally(RuntimeException("Failed on second message"))

            org.mockito.Mockito.doCallRealMethod()
                .doReturn(failedFuture)
                .doCallRealMethod()
                .`when`(spyKafkaTemplate).send(
                    org.mockito.kotlin.any<String>(),
                    org.mockito.kotlin.any<String>(),
                    org.mockito.kotlin.any<String>(),
                )

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
}
