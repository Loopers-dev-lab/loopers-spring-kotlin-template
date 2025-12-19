package com.loopers.domain.event.product

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
    val sortType: String, // ProductSortType을 String으로 직렬화
    val page: Int,
    val browsedAt: Instant = Instant.now()
) : DomainEvent
