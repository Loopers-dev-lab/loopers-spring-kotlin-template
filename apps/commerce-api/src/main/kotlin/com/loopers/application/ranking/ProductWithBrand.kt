package com.loopers.application.ranking

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.product.ProductModel

data class ProductWithBrand(
    val product: ProductModel,
    val brand: BrandModel,
)

