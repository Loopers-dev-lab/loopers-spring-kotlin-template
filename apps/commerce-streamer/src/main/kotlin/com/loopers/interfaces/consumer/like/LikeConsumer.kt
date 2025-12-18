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
class LikeConsumer(private val productMetricFacade: ProductMetricFacade) {

    private val logger = LoggerFactory.getLogger(LikeConsumer::class.java)

    @KafkaListener(
        topics = [EventType.Topic.LIKE_EVENT],
        containerFactory = KafkaConfig.LIKE_METRICS_LISTENER,
    )
    fun listen(
        messages: List<ConsumerRecord<String, String>>,
        acknowledgment: Acknowledgment,
    ) {
        logger.info("Received ${messages.size} like events")

        productMetricFacade.updateLikeCount(messages)

        acknowledgment.acknowledge()
    }
}
