package com.loopers.domain.event

import java.time.LocalDateTime
import java.util.UUID

/**
 * 상품 조회 이벤트
 */
data class ProductViewEvent(
    /**
     * 이벤트 고유 ID (멱등성 보장용)
     */
    val eventId: UUID,

    /**
     * 사용자 ID
     */
    val userId: Long,

    /**
     * 상품 ID
     */
    val productId: Long,

    /**
     * 이벤트 발생 시각
     */
    val createdAt: LocalDateTime,
)
