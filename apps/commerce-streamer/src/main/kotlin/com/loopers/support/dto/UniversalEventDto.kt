package com.loopers.support.dto

/**
 * 범용 이벤트 DTO
 *
 * 모든 토픽의 이벤트를 수용할 수 있는 범용 구조
 */
data class UniversalEventDto(
    var eventId: String? = null,
    var eventType: String? = null,
    var aggregateId: String? = null,
    var timestamp: String? = null,
    var topicName: String? = null,
    var rawPayload: String? = null,
) {
    fun isValid(): Boolean {
        return !eventId.isNullOrBlank() &&
                !eventType.isNullOrBlank() &&
                !aggregateId.isNullOrBlank()
    }
}
