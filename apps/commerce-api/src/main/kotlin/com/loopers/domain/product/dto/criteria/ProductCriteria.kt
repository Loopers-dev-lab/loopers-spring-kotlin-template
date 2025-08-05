package com.loopers.domain.product.dto.criteria

import org.springframework.data.domain.PageRequest

class ProductCriteria {
    data class FindAll(
        val brandIds: List<Long> = mutableListOf(),
        val sort: ProductSortCondition = ProductSortCondition.LATEST,
        val page: Int = 0,
        val size: Int = 20,
    ) {
        enum class ProductSortCondition {
            LATEST,
            CREATED_AT_ASC,
            CREATED_AT_DESC,
            PRICE_ASC,
            PRICE_DESC,
            LIKES_ASC,
            LIKES_DESC,
        }

        fun toPageRequest(): PageRequest {
            return PageRequest.of(page, size)
        }
    }
}
