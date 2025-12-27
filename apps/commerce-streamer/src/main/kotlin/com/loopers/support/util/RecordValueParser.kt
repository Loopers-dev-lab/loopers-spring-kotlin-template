package com.loopers.support.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord

inline fun <reified T> readEvent(
    record: ConsumerRecord<Any, Any>,
    objectMapper: ObjectMapper,
): T {
    val value = record.value()
    return when (value) {
        is ByteArray -> objectMapper.readValue(value, T::class.java)
        is String -> objectMapper.readValue(value, T::class.java)
        else -> objectMapper.convertValue(value, T::class.java)
    }
}
