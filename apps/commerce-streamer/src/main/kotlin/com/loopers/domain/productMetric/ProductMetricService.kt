package com.loopers.domain.productMetric

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class ProductMetricService(
    private val productMetricRepository: ProductMetricRepository,
    private val redisTemplate: RedisTemplate<String, String>,
) {

    private val logger = LoggerFactory.getLogger(ProductMetricService::class.java)

    fun updateLikeCount(likeCountGroupBy: Map<Long, Long>) {
        likeCountGroupBy.forEach { (productId, delta) ->
            val productMetric =
                productMetricRepository.findByRefProductId(productId)
                    ?: ProductMetricModel(refProductId = productId)

            productMetric.updateLikeCount(delta)

            logger.debug(
                "Updated like count for productId: $productId, delta: $delta, new count: ${productMetric.likeCount}",
            )

            productMetricRepository.save(productMetric)
        }
    }

    fun updateViewCount(viewCountGroupBy: Map<Long, Long>) {
        viewCountGroupBy.forEach { (productId, delta) ->
            val productMetric =
                productMetricRepository.findByRefProductId(productId)
                    ?: ProductMetricModel(refProductId = productId)

            productMetric.updateViewCount(delta)
            productMetricRepository.save(productMetric)

            logger.debug(
                "Updated view count for productId: $productId, delta: $delta, new count: ${productMetric.viewCount}",
            )
        }
    }

    fun getAllMetrics() = productMetricRepository.findAll()

    fun getMetricsByProductIds(productIds: Set<Long>) = 
        productMetricRepository.findByRefProductIdIn(productIds)
}
