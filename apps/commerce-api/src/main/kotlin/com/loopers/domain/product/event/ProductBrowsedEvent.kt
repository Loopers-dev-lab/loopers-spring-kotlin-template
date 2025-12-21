package com.loopers.domain.product.event

import com.loopers.domain.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class ProductBrowsedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "PRODUCT_BROWSED",
    override val aggregateId: Long = 0L, // 목록 조회는 특정 상품 없음
    override val occurredAt: Instant = Instant.now(),

    val memberId: String?,
    val brandId: Long?,
    val sortType: String,
    val page: Int,
    val browsedAt: Instant = Instant.now()
) : DomainEvent
