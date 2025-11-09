package com.loopers.application.product

import com.loopers.domain.product.ProductQueryService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class ProductFacade(
    private val productQueryService: ProductQueryService,
) {
    fun getProducts(brandId: Long?, sort: String, pageable: Pageable): Page<ProductListInfo> {
        val productsWithLikeCount = productQueryService.findProducts(brandId, sort, pageable)
        return productsWithLikeCount.map { ProductListInfo.from(it.product, it.likeCount) }
    }

    fun getProductDetail(productId: Long): ProductDetailInfo {
        val productDetail = productQueryService.getProductDetail(productId)
        return ProductDetailInfo.from(productDetail.product, productDetail.stock, productDetail.likeCount)
    }
}
