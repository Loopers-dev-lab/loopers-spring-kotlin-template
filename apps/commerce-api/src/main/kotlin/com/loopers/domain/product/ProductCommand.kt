package com.loopers.domain.product

class ProductCommand {
    data class FindProducts(
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

    data class IncreaseStocks(
        val units: List<IncreaseStockUnit>,
    )

    data class IncreaseStockUnit(
        val productId: Long,
        val amount: Int,
    )
}
