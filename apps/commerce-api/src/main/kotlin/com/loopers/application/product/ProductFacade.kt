package com.loopers.application.product

import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSortType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class ProductFacade(
    private val productService: ProductService,
) {
    fun getProduct(productId: Long): ProductInfo {
        val product = productService.getProduct(productId)
        return ProductInfo.from(product)
    }

    fun getProducts(brandId: Long?, sort: ProductSortType, pageable: Pageable): Page<ProductInfo> {
        val products = productService.getProducts(brandId, sort, pageable)
        return ProductInfo.fromPage(products)
    }

}
