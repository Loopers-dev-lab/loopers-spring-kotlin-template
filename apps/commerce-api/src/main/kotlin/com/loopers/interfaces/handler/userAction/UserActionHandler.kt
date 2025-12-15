package com.loopers.interfaces.handler.userAction

import com.loopers.domain.like.LikedEvent
import com.loopers.domain.like.UnlikedEvent
import com.loopers.domain.order.OrderSuccessEvent
import com.loopers.domain.product.ProductViewedEvent
import com.loopers.domain.userAction.ActionType
import com.loopers.domain.userAction.TargetType
import com.loopers.domain.userAction.UserActionService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class UserActionHandler(private val userActionService: UserActionService) {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleLiked(event: LikedEvent) {
        userActionService.save(event.userId, ActionType.LIKE, TargetType.PRODUCT, event.productId)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleUnliked(event: UnlikedEvent) {
        userActionService.save(event.userId, ActionType.UNLIKE, TargetType.PRODUCT, event.productId)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderSuccess(event: OrderSuccessEvent) {
        userActionService.save(event.userId, ActionType.ORDER, TargetType.ORDER, event.orderId)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductViewed(event: ProductViewedEvent) {
        userActionService.save(event.userId, ActionType.VIEW, TargetType.PRODUCT, event.productId)
    }
}
