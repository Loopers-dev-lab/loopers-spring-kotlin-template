package com.loopers.interfaces.consumer

import com.loopers.application.metrics.ProductMetricsFacade
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.event.OutboxEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * 상품 메트릭 이벤트 Consumer (배치 처리)
 *
 * 좋아요, 조회수 이벤트를 배치로 수신하여 Facade로 위임
 */
@Component
class ProductMetricsConsumer(
    private val productMetricsFacade: ProductMetricsFacade,
) {
    private val log = LoggerFactory.getLogger(ProductMetricsConsumer::class.java)

    /**
     * 좋아요 이벤트 배치 처리
     */
    @KafkaListener(
        topics = [OutboxEvent.LikeCountChanged.TOPIC],
        groupId = "product-metrics-like-consumer",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consumeLikeCountChanged(
        records: List<ConsumerRecord<Any, Any>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("좋아요 이벤트 배치 수신: {} 건", records.size)

        try {
            productMetricsFacade.handleLikeEvents(records)
            acknowledgment.acknowledge()
            log.info("좋아요 이벤트 배치 처리 완료: {} 건", records.size)
        } catch (e: Exception) {
            log.error("좋아요 이벤트 배치 처리 실패", e)
            throw e
        }
    }

    /**
     * 조회수 이벤트 배치 처리
     */
    @KafkaListener(
        topics = [OutboxEvent.ViewCountIncreased.TOPIC],
        groupId = "product-metrics-view-consumer",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consumeViewCountIncreased(
        records: List<ConsumerRecord<Any, Any>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("조회수 이벤트 배치 수신: {} 건", records.size)

        try {
            productMetricsFacade.handleViewEvents(records)
            acknowledgment.acknowledge()
            log.info("조회수 이벤트 배치 처리 완료: {} 건", records.size)
        } catch (e: Exception) {
            log.error("조회수 이벤트 배치 처리 실패", e)
            throw e
        }
    }
}
