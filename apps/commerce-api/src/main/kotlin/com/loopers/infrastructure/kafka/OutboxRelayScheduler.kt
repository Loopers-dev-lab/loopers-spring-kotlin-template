package com.loopers.infrastructure.kafka

import com.loopers.domain.outbox.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Outbox Relay 스케줄러
 * DB의 Outbox 테이블에서 PENDING 이벤트를 조회하여 Kafka로 발행
 * Transactional Outbox Pattern의 핵심 구성 요소
 */
@Component
@ConditionalOnProperty(
    prefix = "kafka.outbox.relay",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
@ConditionalOnBean(KafkaProducerService::class)
class OutboxRelayScheduler(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxEventProcessor: OutboxEventProcessor,
) {
    private val logger = LoggerFactory.getLogger(OutboxRelayScheduler::class.java)

    @Value($$"${kafka.outbox.relay.batch-size:100}")
    private var batchSize: Int = 100

    /**
     * PENDING 상태의 Outbox 이벤트를 Kafka로 발행
     * fixedDelay와 initialDelay는 kafka.yml에서 설정
     */
    @Scheduled(
        fixedDelayString = $$"${kafka.outbox.relay.fixed-delay:5000}",
        initialDelayString = $$"${kafka.outbox.relay.initial-delay:10000}",
    )
    fun relayPendingEvents() {
        try {
            val pendingEvents = outboxEventRepository.findPendingEvents(batchSize)

            if (pendingEvents.isEmpty()) {
                logger.trace("Outbox Relay: PENDING 이벤트 없음")
                return
            }

            logger.info("Outbox Relay 시작: ${pendingEvents.size}개 이벤트 처리")

            var successCount = 0
            var failCount = 0

            pendingEvents.forEach { event ->
                val result = outboxEventProcessor.processEvent(event)
                if (result) {
                    successCount++
                } else {
                    failCount++
                }
            }

            logger.info(
                "Outbox Relay 완료: 성공=$successCount, 실패=$failCount, " +
                        "총=${pendingEvents.size}",
            )
        } catch (e: Exception) {
            logger.error("Outbox Relay 실행 중 오류 발생", e)
        }
    }

    /**
     * 발행 완료된 오래된 이벤트 삭제 (클린업)
     * 매일 새벽 3시에 실행, 7일 이상 된 이벤트 삭제
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun cleanupOldPublishedEvents() {
        try {
            val sevenDaysAgo = java.time.LocalDateTime.now().minusDays(7)
            val deletedCount = outboxEventRepository.deletePublishedEventsBefore(sevenDaysAgo)

            if (deletedCount > 0) {
                logger.info("Outbox 이벤트 클린업 완료: 삭제=$deletedCount")
            }
        } catch (e: Exception) {
            logger.error("Outbox 이벤트 클린업 중 오류 발생", e)
        }
    }
}
