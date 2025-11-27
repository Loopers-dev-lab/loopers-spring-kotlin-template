package com.loopers.application.product

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.cache.CacheTemplate
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductView
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.stereotype.Component

@Component
class ProductFacade(
    private val productService: ProductService,
    private val cacheTemplate: CacheTemplate,
) {
    companion object {
        private val TYPE_PRODUCT_VIEW = object : TypeReference<ProductView>() {}
        private val TYPE_CACHED_PRODUCT_LIST = object : TypeReference<CachedProductList>() {}
    }

    fun findProductById(id: Long): ProductInfo.FindProductById {
        val cacheKey = ProductCacheKeys.ProductDetail(productId = id)

        val productView = cacheTemplate.cacheAside(
            cacheKey = cacheKey,
            typeReference = TYPE_PRODUCT_VIEW,
        ) {
            productService.findProductViewById(id)
        }

        return ProductInfo.FindProductById.from(productView)
    }

    fun findProducts(criteria: ProductCriteria.FindProducts): ProductInfo.FindProducts {
        val cacheKey = ProductCacheKeys.ProductList(
            sort = criteria.sort,
            brandId = criteria.brandId,
            page = criteria.page,
            size = criteria.size,
        )

        // 캐시 기준 이내만 캐시
        if (!cacheKey.shouldCache()) {
            return ProductInfo.FindProducts.from(productService.findProducts(criteria.to()))
        }

        val cachedList = cacheTemplate.get(cacheKey, TYPE_CACHED_PRODUCT_LIST)

        // 캐시 히트: ID 목록으로 상품 상세 일괄 조회
        if (cachedList != null) {
            val productViews = productService.findAllProductViewByIds(cachedList.productIds)

            // Slice 재구성
            val command = criteria.to()
            val pageable = PageRequest.of(command.page ?: 0, command.size ?: 20)

            return ProductInfo.FindProducts.from(
                SliceImpl(productViews, pageable, cachedList.hasNext),
            )
        }

        // 캐시 미스: DB 조회
        val slice = productService.findProducts(criteria.to())

        val cachedProductList = CachedProductList(
            productIds = slice.content.map { it.product.id },
            hasNext = slice.hasNext(),
        )

        // 조회 결과 캐싱
        cacheTemplate.put(cacheKey, cachedProductList)

        val productCacheKeys = slice.content
            .associateBy { ProductCacheKeys.ProductDetail(productId = it.product.id) }

        cacheTemplate.putAll(productCacheKeys)

        return ProductInfo.FindProducts.from(slice)
    }
}
