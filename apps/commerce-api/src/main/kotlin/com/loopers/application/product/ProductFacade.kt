package com.loopers.application.product

import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductFacade(
    private val productService: ProductService,
) {
    @Transactional(readOnly = true)
    fun findProductById(id: Long): ProductInfo.FindProductById {
        return ProductInfo.FindProductById(productService.findProductViewById(id))
    }

    @Transactional(readOnly = true)
    fun searchProducts(criteria: ProductCriteria.SearchProducts): ProductInfo.SearchProducts {
        return ProductInfo.SearchProducts(productService.searchProducts(criteria.to()))
    }
}
