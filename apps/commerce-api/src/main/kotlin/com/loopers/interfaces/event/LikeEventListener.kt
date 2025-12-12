package com.loopers.interfaces.event

import com.loopers.domain.like.LikeCanceledEventV1
import com.loopers.domain.like.LikeCreatedEventV1
import com.loopers.domain.product.ProductService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class LikeEventListener(
    private val productService: ProductService,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onLikeCreated(event: LikeCreatedEventV1) {
        productService.increaseProductLikeCount(event.productId)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onLikeCanceled(event: LikeCanceledEventV1) {
        productService.decreaseProductLikeCount(event.productId)
    }
}
