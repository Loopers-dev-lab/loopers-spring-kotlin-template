package com.loopers.interfaces.consumer

import com.loopers.application.audit.AuditLogFacade
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.event.OutboxEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * 감사 로그 이벤트 Consumer (배치 처리)
 *
 * 모든 비즈니스 이벤트를 빠짐없이 기록
 * - 별도 컨슈머 그룹으로 독립적인 오프셋 관리
 * - 원본 데이터 보존
 * - 모든 토픽을 하나의 리스너로 처리
 */
@Component
class AuditLogEventConsumer(
    private val auditLogFacade: AuditLogFacade,
) {
    private val log = LoggerFactory.getLogger(AuditLogEventConsumer::class.java)

    /**
     * 모든 비즈니스 이벤트 감사 로그
     *
     * 하나의 리스너로 모든 토픽을 구독하여 처리
     */
    @KafkaListener(
        topics = [
            OutboxEvent.LikeCountChanged.TOPIC,
            OutboxEvent.ViewCountIncreased.TOPIC,
            OutboxEvent.OrderCompleted.TOPIC,
            OutboxEvent.OrderCanceled.TOPIC,
            OutboxEvent.SoldOut.TOPIC,
        ],
        groupId = "audit-collector",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun auditAllEvents(
        records: List<ConsumerRecord<Any, Any>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("감사 로그 배치 수신: {} 건", records.size)

        try {
            auditLogFacade.processAuditBatch(records)
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            log.error("감사 로그 배치 처리 실패", e)
            throw e
        }
    }
}
