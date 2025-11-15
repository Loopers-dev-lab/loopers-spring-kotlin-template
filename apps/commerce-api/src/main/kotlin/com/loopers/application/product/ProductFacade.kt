package com.loopers.application.product

import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component

@Component
class ProductFacade(
    private val productService: ProductService,
) {
    fun findProductById(id: Long): ProductInfo.FindProductById {
        return ProductInfo.FindProductById(productService.findProductViewById(id))
    }

    fun findProducts(criteria: ProductCriteria.FindProducts): ProductInfo.FindProducts {
        return ProductInfo.FindProducts(productService.findProducts(criteria.to()))
    }
}
