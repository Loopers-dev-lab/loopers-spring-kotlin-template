package com.loopers.application.product

import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSortType
import com.loopers.infrastructure.product.ProductCacheStore
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class ProductFacade(
    private val productService: ProductService,
    private val productCacheStore: ProductCacheStore,
) {
    fun getProduct(productId: Long): ProductInfo {
        val cached = productCacheStore.getProduct(productId)
        if (cached != null) return cached

        val product = productService.getProduct(productId)
        val productInfo = ProductInfo.from(product)
        productCacheStore.setProduct(productId, productInfo)
        return productInfo
    }

    fun getProducts(brandId: Long?, sort: ProductSortType, pageable: Pageable): Page<ProductInfo> {
        val cachedPage = productCacheStore.getProductList(brandId, sort, pageable.pageNumber)
        if (cachedPage != null) {
            return PageImpl(cachedPage.content, pageable, cachedPage.totalElements)
        }

        val products = productService.getProducts(brandId, sort, pageable)
        val productInfoPage = ProductInfo.fromPage(products)

        productCacheStore.setProductList(
            brandId,
            sort,
            pageable.pageNumber,
            productInfoPage.content,
            productInfoPage.totalElements
        )

        return productInfoPage
    }
}
