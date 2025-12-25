package com.loopers.interfaces.consumer.like

import com.loopers.application.productMetric.ProductMetricFacade
import com.loopers.config.kafka.KafkaConfig
import com.loopers.event.EventType
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CatalogConsumer(private val productMetricFacade: ProductMetricFacade) {

    private val logger = LoggerFactory.getLogger(CatalogConsumer::class.java)

    @KafkaListener(
        topics = [EventType.Topic.CATALOG_EVENT],
        containerFactory = KafkaConfig.CATALOG_BATCH_LISTENER,
    )
    fun listen(
        messages: List<ConsumerRecord<String, String>>,
        acknowledgment: Acknowledgment,
    ) {
        logger.info("Received ${messages.size} like events")

        try {
            val updatedProductIds = productMetricFacade.updateProductMetrics(messages)
            // 변경된 상품들에 대해 즉시 랭킹 업데이트
            if (updatedProductIds.isNotEmpty()) {
                productMetricFacade.updateRankingForProducts(updatedProductIds)
            }
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error(e.message, e)
            acknowledgment.acknowledge()
        }
    }
}
