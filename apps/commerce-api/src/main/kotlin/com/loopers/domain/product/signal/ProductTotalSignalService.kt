package com.loopers.domain.product.signal

import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

@Component
class ProductTotalSignalService(private val productTotalSignalRepository: ProductTotalSignalRepository) {

    @Transactional
    fun incrementLikeCount(productId: Long) {
        val totalSignal = productTotalSignalRepository.getByProductIdWithPessimisticLock(productId)
        totalSignal.incrementLikeCount()

        productTotalSignalRepository.save(totalSignal)
    }

    @Transactional
    fun decrementLikeCount(productId: Long) {
        val totalSignal = productTotalSignalRepository.getByProductIdWithPessimisticLock(productId)
        totalSignal.decrementLikeCount()

        productTotalSignalRepository.save(totalSignal)
    }
}
