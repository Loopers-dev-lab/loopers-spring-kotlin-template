package com.loopers.domain.product.signal

import org.springframework.stereotype.Component

@Component
class ProductTotalSignalService(private val productTotalSignalRepository: ProductTotalSignalRepository) {

    fun incrementLikeCount(productId: Long) {
        val totalSignal = productTotalSignalRepository.getByProductIdWithPessimisticLock(productId)
        totalSignal.incrementLikeCount()

        productTotalSignalRepository.save(totalSignal)
    }

    fun decrementLikeCount(productId: Long) {
        val totalSignal = productTotalSignalRepository.getByProductIdWithPessimisticLock(productId)
        totalSignal.decrementLikeCount()

        productTotalSignalRepository.save(totalSignal)
    }
}
