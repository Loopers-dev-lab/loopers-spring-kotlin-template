package com.loopers.interfaces.api.product.request

import com.loopers.domain.product.dto.criteria.ProductCriteria
import com.loopers.domain.product.dto.criteria.ProductCriteria.FindAll.ProductSortCondition

class ProductV1Request {
    data class GetProducts(
        val brandIds: List<Long> = mutableListOf(),
        val sort: ProductSortCondition = ProductSortCondition.LATEST,
        val page: Int = 0,
        val size: Int = 20,
    ) {
        fun toCriteria(): ProductCriteria.FindAll {
            return ProductCriteria.FindAll(
                brandIds = brandIds,
                sort = sort,
                page = page,
                size = size,
            )
        }
    }
}
