package com.loopers.application.product

import com.loopers.application.dto.PageResult
import com.loopers.domain.product.ProductSort
import org.springframework.data.domain.Pageable

interface ProductCache {
    fun getProductDetail(productId: Long, userId: String?): ProductResult.DetailInfo?
    fun setProductDetail(productId: Long, userId: String?, value: ProductResult.DetailInfo)
    fun getProductList(brandId: Long?, sort: ProductSort, pageable: Pageable): PageResult<ProductResult.ListInfo>?
    fun setProductList(brandId: Long?, sort: ProductSort, pageable: Pageable, value: PageResult<ProductResult.ListInfo>)
    fun getLikedProductList(userId: String, pageable: Pageable): PageResult<ProductResult.LikedInfo>?
    fun setLikedProductList(userId: String, pageable: Pageable, value: PageResult<ProductResult.LikedInfo>)
    fun evictProductDetail(productId: Long)
    fun evictProductList()
    fun evictLikedProductList(userId: String)
}
