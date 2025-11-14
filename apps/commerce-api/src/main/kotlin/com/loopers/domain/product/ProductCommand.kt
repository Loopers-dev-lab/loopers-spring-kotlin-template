package com.loopers.domain.product

class ProductCommand {
    data class FindProductViewsBy(
        val page: Int? = null,
        val size: Int? = null,
        val sort: ProductSortType? = null,
        val brandId: Long? = null,
    ) {
        fun to(): PageQuery {
            return PageQuery.of(
                page = page,
                size = size,
                sort = sort,
                brandId = brandId,
            )
        }
    }

    data class DecreaseStocks(
        val units: List<DecreaseStockUnit>,
    )

    data class DecreaseStockUnit(
        val productId: Long,
        val amount: Int,
    )
}
