package com.loopers.domain.product.event

import java.time.Instant

data class ProductViewedEvent(
    val productId: Long,
    val memberId: String?,  // 비로그인 사용자는 null
    val viewedAt: Instant
)
