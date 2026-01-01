package com.loopers.application.product

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.cache.CacheTemplate
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductViewedEventV1
import com.loopers.domain.ranking.ProductRankingReader
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingQuery
import java.time.Clock
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.stereotype.Component

@Component
class ProductFacade(
    private val productService: ProductService,
    private val cacheTemplate: CacheTemplate,
    private val eventPublisher: ApplicationEventPublisher,
    private val productRankingReader: ProductRankingReader,
    private val clock: Clock,
) {
    companion object {
        private val TYPE_CACHED_PRODUCT_DETAIL_V1 = object : TypeReference<CachedProductDetailV1>() {}
        private val TYPE_CACHED_PRODUCT_LIST = object : TypeReference<CachedProductList>() {}
    }

    fun findProductById(id: Long, userId: Long? = null): ProductInfo.FindProductById {
        val cacheKey = ProductCacheKeys.ProductDetail(productId = id)

        val cached = cacheTemplate.get(cacheKey, TYPE_CACHED_PRODUCT_DETAIL_V1)

        val productView = if (cached != null) {
            cached.toProductView()
        } else {
            val view = productService.findProductViewById(id)
            cacheTemplate.put(cacheKey, CachedProductDetailV1.from(view))
            view
        }

        val query = RankingQuery(
            period = RankingPeriod.HOURLY,
            dateTime = clock.instant(),
            offset = 0,
            limit = 1,
        )
        val rank = productRankingReader.findRankByProductId(query, id)

        eventPublisher.publishEvent(ProductViewedEventV1.create(id, userId))

        return ProductInfo.FindProductById.from(productView, rank)
    }

    fun findProducts(criteria: ProductCriteria.FindProducts): ProductInfo.FindProducts {
        val command = criteria.to()

        // TODO(toong): facade에서 도메인 모델을 직접적으로 의존하고, 그 값을 기본값 추출용으로만 사용하고 있는 상태. 해결필요
        val pageQuery = command.to()

        val cacheKey = ProductCacheKeys.ProductList.from(pageQuery)

        // 캐시 기준 이내만 캐시
        if (!cacheKey.shouldCache()) {
            return ProductInfo.FindProducts.from(productService.findProducts(command))
        }

        val cachedList = cacheTemplate.get(cacheKey, TYPE_CACHED_PRODUCT_LIST)

        // 캐시 히트: detail 캐시들 getAll 조회
        if (cachedList != null) {
            val detailCacheKeys = cachedList.productIds.map {
                ProductCacheKeys.ProductDetail(productId = it)
            }

            val cachedProducts = cacheTemplate.getAll(detailCacheKeys, TYPE_CACHED_PRODUCT_DETAIL_V1)
                .map { it.toProductView() }

            val cachedMap = cachedProducts.associateBy { it.productId }

            val cachedIds = cachedMap.keys
            val missingIds = cachedList.productIds.filterNot { it in cachedIds }

            // 캐시 미스된 상품들만 DB 조회
            val dbProducts = productService.findAllProductViewByIds(missingIds)

            val dbMap = dbProducts.associateBy { it.productId }

            val orderedProducts = cachedList.productIds.mapNotNull { id ->
                cachedMap[id] ?: dbMap[id]
            }

            // 누락된 상품들 detail 캐싱
            val dbCacheMap = dbProducts.associate {
                ProductCacheKeys.ProductDetail(productId = it.productId) to CachedProductDetailV1.from(it)
            }
            cacheTemplate.putAll(dbCacheMap)

            // Slice 재구성
            val pageable = PageRequest.of(pageQuery.page, pageQuery.size)

            return ProductInfo.FindProducts.from(
                SliceImpl(orderedProducts, pageable, cachedList.hasNext),
            )
        }

        // 캐시 미스: DB 조회
        val slice = productService.findProducts(command)

        val cachedProductList = CachedProductList(
            productIds = slice.content.map { it.productId },
            hasNext = slice.hasNext(),
        )

        // 조회 결과 캐싱
        cacheTemplate.put(cacheKey, cachedProductList)

        val productCacheKeys = slice.content.associate {
            ProductCacheKeys.ProductDetail(productId = it.productId) to CachedProductDetailV1.from(it)
        }

        cacheTemplate.putAll(productCacheKeys)

        return ProductInfo.FindProducts.from(slice)
    }
}
