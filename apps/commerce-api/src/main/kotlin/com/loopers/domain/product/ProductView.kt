package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class ProductView(
    val product: Product,
    val stock: Stock,
    val statistic: ProductStatistic,
    val brand: Brand,
) {
    init {
        if (product.id != stock.productId) {
            throw CoreException(ErrorType.BAD_REQUEST, "product와 stock의 id가 일치하지 않습니다.")
        }

        if (product.brandId != brand.id) {
            throw CoreException(ErrorType.BAD_REQUEST, "product와 brand의 id가 일치하지 않습니다.")
        }

        if (product.id != statistic.productId) {
            throw CoreException(ErrorType.BAD_REQUEST, "product와 statistic의 id가 일치하지 않습니다.")
        }
    }
}
