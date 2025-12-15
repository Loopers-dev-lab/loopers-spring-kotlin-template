package com.loopers.interfaces.handler.like

import com.loopers.domain.like.LikedEvent
import com.loopers.domain.like.UnlikedEvent
import com.loopers.domain.product.signal.ProductTotalSignalService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class LikeEventHandler(private val productTotalSignalService: ProductTotalSignalService) {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleLiked(event: LikedEvent) {
        productTotalSignalService.incrementLikeCount(event.productId)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleUnliked(event: UnlikedEvent) {
        productTotalSignalService.decrementLikeCount(event.productId)
    }
}
