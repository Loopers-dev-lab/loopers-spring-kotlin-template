package com.loopers.infrastructure.product

import com.querydsl.core.annotations.QueryProjection
import java.math.BigDecimal
import java.time.ZonedDateTime

data class ProductWithDetailsProjection @QueryProjection constructor(
    val productId: Long,
    val productName: String,
    val price: BigDecimal,
    val brandId: Long,
    val brandName: String,
    val likeCount: Long,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
)
