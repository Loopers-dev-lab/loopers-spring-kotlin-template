package com.loopers.application.product

import com.loopers.domain.product.ProductDetailService
import com.loopers.domain.product.ProductService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class ProductFacade(private val productService: ProductService, private val productDetailService: ProductDetailService) {

    fun getProductDetail(productId: Long) = productDetailService.getProductDetailBy(productId)

    fun getProducts(pageable: Pageable, brandId: Long?): Page<ProductInfo> = productService.getProducts(
        pageable,
        brandId,
    )
}
