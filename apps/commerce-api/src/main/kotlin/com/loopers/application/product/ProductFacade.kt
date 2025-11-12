package com.loopers.application.product

import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductResult
import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val likeService: LikeService,
) {

    @Transactional(readOnly = true)
    fun getProductInfo(productId: Long): ProductResult.ProductInfo {
        val product = productService.getProduct(productId)
        val brand = brandService.getBrand(product.brandId)
        val likeCount = likeService.countLikesByProductId(productId)

        return ProductResult.ProductInfo.of(product, brand, likeCount)
    }
}
