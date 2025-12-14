package com.loopers.domain.like.event

import com.loopers.domain.product.ProductRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProductLikeEventHandler(
    @Qualifier("eventCoroutineScope")
    private val coroutineScope: CoroutineScope,
    private val productLikeEventProcessor: ProductLikeEventProcessor
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductLiked(event: ProductLikedEvent) {
        coroutineScope.launch {
            try {
                productLikeEventProcessor.processProductLiked(event.productId)
            } catch (e: Exception) {
                logger.error("좋아요 집계 실패: productId=${event.productId}", e)
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductUnliked(event: ProductUnlikedEvent) {
        coroutineScope.launch {
            try {
                productLikeEventProcessor.processProductUnliked(event.productId)
            } catch (e: Exception) {
                logger.error("좋아요 취소 집계 실패: productId=${event.productId}", e)
            }
        }
    }
}

@Component
class ProductLikeEventProcessor(
    private val productRepository: ProductRepository
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processProductLiked(productId: Long) {
        val product = productRepository.findByIdWithLockOrThrow(productId)
        product.increaseLikesCount()
        productRepository.save(product)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processProductUnliked(productId: Long) {
        val product = productRepository.findByIdWithLockOrThrow(productId)
        product.decreaseLikesCount()
        productRepository.save(product)
    }
}
