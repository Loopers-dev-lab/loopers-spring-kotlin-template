package com.loopers.application.product

import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductSortType

class ProductCriteria {
    data class FindProducts(
        val page: Int? = null,
        val size: Int? = null,
        val sort: ProductSortType? = null,
        val brandId: Long? = null,
    ) {
        fun to(): ProductCommand.FindProducts {
            return ProductCommand.FindProducts(
                page = page,
                size = size,
                sort = sort,
                brandId = brandId,
            )
        }
    }
}
