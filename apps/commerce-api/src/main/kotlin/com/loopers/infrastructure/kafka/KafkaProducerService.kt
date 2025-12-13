package com.loopers.infrastructure.kafka

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

/**
 * Kafka Producer Service
 * Outbox Relay에서 사용하여 실제로 Kafka로 메시지 전송
 */
@Service
@ConditionalOnBean(KafkaTemplate::class)
class KafkaProducerService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {
    private val logger = LoggerFactory.getLogger(KafkaProducerService::class.java)

    /**
     * 메시지를 Kafka로 전송
     * @param topic Kafka 토픽
     * @param key 파티션 키 (순서 보장을 위해 중요)
     * @param message 메시지 (JSON)
     * @return 전송 결과 Future
     */
    fun send(topic: String, key: String, message: String): CompletableFuture<SendResult<String, String>> {
        logger.debug("Kafka 메시지 전송 시작: topic=$topic, key=$key")

        val future = kafkaTemplate.send(topic, key, message)

        future.whenComplete { result, ex ->
            if (ex == null) {
                logger.debug(
                    "Kafka 메시지 전송 성공: topic=$topic, key=$key, " +
                        "partition=${result.recordMetadata.partition()}, " +
                        "offset=${result.recordMetadata.offset()}",
                )
            } else {
                logger.error("Kafka 메시지 전송 실패: topic=$topic, key=$key", ex)
            }
        }

        return future
    }
}
