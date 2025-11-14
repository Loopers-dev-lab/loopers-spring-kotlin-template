package com.loopers.application.product

import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductSortType

class ProductCriteria {
    data class SearchProducts(
        val page: Int? = null,
        val size: Int? = null,
        val sort: ProductSortType? = null,
        val brandId: Long? = null,
    ) {
        fun to(): ProductCommand.SearchProducts {
            return ProductCommand.SearchProducts(
                page = page,
                size = size,
                sort = sort,
                brandId = brandId,
            )
        }
    }
}
