package com.loopers.domain.event

import java.time.Instant

/**
 * 모든 도메인 이벤트의 기본 인터페이스
 * Kafka 이벤트 파이프라인에서 사용
 */
interface DomainEvent {

    /**
     * 이벤트 고유 ID (멱등성 체크용)
     */
    val eventId: String

    /**
     * 이벤트 타입 (PRODUCT_LIKED, ORDER_CREATED 등)
     */
    val eventType: String

    /**
     * Aggregate ID (Kafka Partition Key로 사용)
     * - productId, orderId 등
     * - 같은 Aggregate의 이벤트는 순서 보장
     */
    val aggregateId: Long

    /**
     * 이벤트 발생 시각
     */
    val occurredAt: Instant
}
