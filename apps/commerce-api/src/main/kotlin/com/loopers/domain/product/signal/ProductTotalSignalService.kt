package com.loopers.domain.product.signal

import org.springframework.stereotype.Component

@Component
class ProductTotalSignalService(private val productTotalSignalRepository: ProductTotalSignalRepository) {

    fun incrementLikeCount(productId: Long) {
        val totalSignal = productTotalSignalRepository.findByProductId(productId)
        totalSignal?.incrementLikeCount()
    }

    fun decrementLikeCount(productId: Long) {
        val totalSignal = productTotalSignalRepository.findByProductId(productId)
        totalSignal?.decrementLikeCount()
    }
}
