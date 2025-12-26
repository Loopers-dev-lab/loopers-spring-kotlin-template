package com.loopers.domain.product

import com.loopers.application.product.ProductCacheKeys
import com.loopers.cache.CacheTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * ProductCacheService - 상품 캐시 관리 서비스
 *
 * - 재고 소진, 가격 변경 등 이벤트에 따른 캐시 무효화 처리
 * - CacheTemplate을 통한 Redis 캐시 연동
 */
@Service
class ProductCacheService(
    private val cacheTemplate: CacheTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 재고 소진된 상품의 캐시를 무효화
     *
     * @param command 재고 소진 상품 ID 목록
     */
    fun evictStockDepletedProducts(command: EvictStockDepletedCommand) {
        if (command.productIds.isEmpty()) return

        val cacheKeys = command.productIds.map { ProductCacheKeys.ProductDetail(it) }
        cacheTemplate.evictAll(cacheKeys)
        log.debug("Evicted cache for {} stock depleted products", command.productIds.size)
    }
}
