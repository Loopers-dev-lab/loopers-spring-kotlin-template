package com.loopers.domain.event

import java.time.ZonedDateTime
import java.util.UUID

/**
 * 좋아요 추가 이벤트
 * 사용자가 상품에 좋아요를 추가했을 때 발행
 */
data class LikeAddedEvent(
    val eventId: UUID,
    val userId: Long,
    val productId: Long,
    val createdAt: ZonedDateTime = ZonedDateTime.now(),
)

/**
 * 좋아요 제거 이벤트
 * 사용자가 상품의 좋아요를 제거했을 때 발행
 */
data class LikeRemovedEvent(
    val eventId: UUID,
    val userId: Long,
    val productId: Long,
    val createdAt: ZonedDateTime = ZonedDateTime.now(),
)
