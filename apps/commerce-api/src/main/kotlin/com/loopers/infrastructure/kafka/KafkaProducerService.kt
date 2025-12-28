package com.loopers.infrastructure.kafka

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

/**
 * Kafka Producer Service
 * Outbox Relay에서 사용하여 실제로 Kafka로 메시지 전송
 */
@Service
class KafkaProducerService(private val kafkaTemplate: KafkaTemplate<Any, Any>) {
    private val logger = LoggerFactory.getLogger(KafkaProducerService::class.java)

    /**
     * 메시지를 Kafka로 전송
     * @param topic Kafka 토픽
     * @param key 파티션 키 (순서 보장을 위해 중요)
     * @param message 메시지 (JSON)
     * @param eventType 이벤트 타입 (Consumer에서 라우팅에 사용)
     * @return 전송 결과 Future
     */
    fun send(
        topic: String,
        key: String,
        message: String,
        eventType: String,
    ): CompletableFuture<SendResult<Any, Any>> {
        logger.debug("Kafka 메시지 전송 시작: topic=$topic, key=$key, eventType=$eventType")

        // ProducerRecord 생성 및 eventType 헤더 추가
        val record = ProducerRecord<Any, Any>(topic, null, key, message)
        record.headers().add(RecordHeader("eventType", eventType.toByteArray()))

        val future = kafkaTemplate.send(record)

        future.whenComplete { result, ex ->
            if (ex == null) {
                logger.debug(
                    "Kafka 메시지 전송 성공: topic=$topic, key=$key, eventType=$eventType, " +
                            "partition=${result.recordMetadata.partition()}, " +
                            "offset=${result.recordMetadata.offset()}",
                )
            } else {
                logger.error("Kafka 메시지 전송 실패: topic=$topic, key=$key, eventType=$eventType", ex)
            }
        }

        return future
    }
}
