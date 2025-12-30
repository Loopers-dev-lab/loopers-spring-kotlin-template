package com.loopers.interfaces.consumer

import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

@Component
class DemoKafkaConsumer {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${demo-kafka.test.topic-name}"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun demoListener(
        messages: List<ConsumerRecord<Any, Any>>,
        acknowledgment: Acknowledgment,
    ) {
        logger.info("Received messages: {}", messages)
        acknowledgment.acknowledge() // manual ack
    }
}
