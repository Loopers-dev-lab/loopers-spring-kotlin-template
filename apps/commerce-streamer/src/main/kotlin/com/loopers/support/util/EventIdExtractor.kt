package com.loopers.support.util

import org.apache.kafka.clients.consumer.ConsumerRecord

/**
 * Kafka 메시지 헤더에서 eventId를 추출하는 유틸리티
 */
object EventIdExtractor {
    private const val EVENT_ID_HEADER = "eventId"

    fun extract(record: ConsumerRecord<Any, Any>): String {
        val header = record.headers().lastHeader(EVENT_ID_HEADER)
            ?: throw IllegalArgumentException(
                "eventId 헤더가 없습니다: topic=${record.topic()}, partition=${record.partition()}, " +
                        "offset=${record.offset()}",
            )
        return String(header.value())
    }
}
