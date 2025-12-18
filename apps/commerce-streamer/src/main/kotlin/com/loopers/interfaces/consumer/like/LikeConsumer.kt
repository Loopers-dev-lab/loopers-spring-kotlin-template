package com.loopers.interfaces.consumer.like

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.event.EventHandleService
import com.loopers.domain.productMetric.ProductMetricService
import com.loopers.event.EventType
import com.loopers.event.LikeEventPayload
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class LikeConsumer(
   private val productMetricFacade:
) {

    private val logger = LoggerFactory.getLogger(LikeConsumer::class.java)

    @KafkaListener(
            topics = [EventType.Topic.LIKE_EVENT],
            containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun listen(
            messages: List<ConsumerRecord<String, String>>,
            acknowledgment: Acknowledgment,
    ) {
        logger.info("Received ${messages.size} like events")
        acknowledgment.acknowledge()
    }
}
