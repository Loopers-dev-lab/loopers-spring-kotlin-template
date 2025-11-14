package com.loopers.application.product

import com.loopers.domain.product.ProductView
import org.springframework.data.domain.Slice

class ProductInfo {
    data class FindProductById(
        val product: ProductView,
    )

    data class SearchProducts(
        val products: Slice<ProductView>,
    )
}
