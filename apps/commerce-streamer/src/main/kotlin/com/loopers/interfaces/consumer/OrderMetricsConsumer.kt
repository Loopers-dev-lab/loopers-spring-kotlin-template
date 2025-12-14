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
 * 주문 메트릭 이벤트 Consumer (배치 처리)
 *
 * 주문 완료/취소 이벤트를 배치로 수신하여 Facade로 위임
 */
@Component
class OrderMetricsConsumer(
    private val productMetricsFacade: ProductMetricsFacade,
) {
    private val log = LoggerFactory.getLogger(OrderMetricsConsumer::class.java)

    /**
     * 주문 완료 이벤트 배치 처리
     */
    @KafkaListener(
        topics = [OutboxEvent.OrderCompleted.TOPIC],
        groupId = "order-metrics-consumer",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consumeOrderCompleted(
        records: List<ConsumerRecord<Any, Any>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("주문 완료 이벤트 배치 수신: {} 건", records.size)

        try {
            productMetricsFacade.handleOrderCompletedEvents(records)
            acknowledgment.acknowledge()
            log.info("주문 완료 이벤트 배치 처리 완료: {} 건", records.size)
        } catch (e: Exception) {
            log.error("주문 완료 이벤트 배치 처리 실패", e)
            throw e
        }
    }

    /**
     * 주문 취소 이벤트 배치 처리
     */
    @KafkaListener(
        topics = [OutboxEvent.OrderCanceled.TOPIC],
        groupId = "order-metrics-consumer",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consumeOrderCanceled(
        records: List<ConsumerRecord<Any, Any>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("주문 취소 이벤트 배치 수신: {} 건", records.size)

        try {
            productMetricsFacade.handleOrderCanceledEvents(records)
            acknowledgment.acknowledge()
            log.info("주문 취소 이벤트 배치 처리 완료: {} 건", records.size)
        } catch (e: Exception) {
            log.error("주문 취소 이벤트 배치 처리 실패", e)
            throw e
        }
    }
}
