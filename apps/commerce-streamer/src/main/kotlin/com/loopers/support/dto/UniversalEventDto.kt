package com.loopers.support.dto

/**
 * 범용 이벤트 DTO
 *
 * 모든 토픽의 이벤트를 수용할 수 있는 범용 구조
 */
data class UniversalEventDto(
    val eventId: String? = null,
    val eventType: String? = null,
    val aggregateId: String? = null,
    val timestamp: String? = null,
    val topicName: String? = null,
    val rawPayload: String? = null,
)
