package com.loopers.domain.productMetric

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ProductMetricService(private val productMetricRepository: ProductMetricRepository) {
    private val logger = LoggerFactory.getLogger(ProductMetricService::class.java)

    fun updateLikeCount(likeCountGroupBy: Map<Long, Long>) {
        likeCountGroupBy.forEach { (productId, delta) ->
            val productMetric =
                    productMetricRepository.findByRefProductId(productId)
                            ?: ProductMetricModel(refProductId = productId)

            productMetric.updateLikeCount(delta)
            productMetricRepository.save(productMetric)

            logger.debug(
                    "Updated like count for productId: $productId, delta: $delta, new count: ${productMetric.likeCount}"
            )
        }
    }
}
