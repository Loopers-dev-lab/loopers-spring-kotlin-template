package com.loopers.application.product

import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component

@Component
class ProductFacade(
    private val productService: ProductService,
) {
    fun findProductById(id: Long): ProductInfo.FindProductById {
        val productView = productService.findProductViewById(id)
        return ProductInfo.FindProductById.from(productView)
    }

    fun findProducts(criteria: ProductCriteria.FindProducts): ProductInfo.FindProducts {
        val slice = productService.findProducts(criteria.to())
        return ProductInfo.FindProducts.from(slice)
    }
}
