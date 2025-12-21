package com.loopers.domain.event

/**
 * Aggregate 타입과 Kafka Topic 매핑
 * - 새로운 도메인 추가 시 Enum만 추가
 */
enum class AggregateType(val topic: String) {
    PRODUCT("catalog-events"),
    ORDER("order-events"),
    MEMBER("member-events");


    companion object {
        fun fromEventType(eventType: String): AggregateType {
            /**
             * 이벤트 타입으로 부터 Aggregate 타입 추론
             * - PRODUCT_LIKED -> PRODUCT
             * - ORDER_CREATED -> ORDER
             */
            return when {
                eventType.startsWith("PRODUCT_") -> PRODUCT
                eventType.startsWith("ORDER_") -> ORDER
                eventType.startsWith("MEMBER_") -> MEMBER
                else -> throw IllegalArgumentException("Unknown event type: $eventType")
            }
        }
    }
}
