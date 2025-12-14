package com.loopers.domain.product.event

import com.loopers.domain.product.ProductSortType
import java.time.Instant

data class ProductBrowsedEvent(
    val memberId: String?,
    val brandId: Long?,
    val sortType: ProductSortType,
    val page: Int,
    val browsedAt: Instant
)
