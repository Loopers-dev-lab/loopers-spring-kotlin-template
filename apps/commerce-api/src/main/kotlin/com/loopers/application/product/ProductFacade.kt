package com.loopers.application.product

import com.loopers.domain.product.ProductDetailService
import com.loopers.domain.product.ProductService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class ProductFacade(private val productService: ProductService, private val productDetailService: ProductDetailService) {

    fun getProductDetail(productId: Long) = productDetailService.getProductDetailBy(productId)

    fun getAllProducts(sort: String, direction: String, page: Int, size: Int): Page<ProductInfo> = productService.getProducts(
        sort,
        direction,
        page,
        size,
    )
}
