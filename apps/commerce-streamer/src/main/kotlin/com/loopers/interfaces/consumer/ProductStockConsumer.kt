package com.loopers.interfaces.consumer

import com.loopers.application.stock.ProductStockFacade
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.event.OutboxEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * 상품 재고 이벤트 Consumer (배치 처리)
 *
 * 품절 이벤트를 배치로 수신하여 Facade로 위임
 */
@Component
class ProductStockConsumer(
    private val productStockFacade: ProductStockFacade,
) {
    private val log = LoggerFactory.getLogger(ProductStockConsumer::class.java)

    /**
     * 품절 이벤트 배치 처리
     */
    @KafkaListener(
        topics = [OutboxEvent.SoldOut.TOPIC],
        groupId = "product-stock-consumer",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consumeSoldOut(
        records: List<ConsumerRecord<Any, Any>>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("품절 이벤트 배치 수신: {} 건", records.size)

        try {
            productStockFacade.handleSoldOutEvents(records)
            acknowledgment.acknowledge()
            log.info("품절 이벤트 배치 처리 완료: {} 건", records.size)
        } catch (e: Exception) {
            log.error("품절 이벤트 배치 처리 실패", e)
            throw e
        }
    }
}
