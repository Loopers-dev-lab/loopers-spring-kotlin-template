package com.loopers.interfaces.event.product

import com.loopers.domain.like.LikeCanceledEventV1
import com.loopers.domain.like.LikeCreatedEventV1
import com.loopers.domain.order.OrderCanceledEventV1
import com.loopers.domain.order.OrderCreatedEventV1
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProductEventListener(
    private val productService: ProductService,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onOrderCreated(event: OrderCreatedEventV1) {
        val command = ProductCommand.DecreaseStocks(
            units = event.orderItems.map {
                ProductCommand.DecreaseStockUnit(it.productId, it.quantity)
            },
        )
        productService.decreaseStocks(command)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onOrderCanceled(event: OrderCanceledEventV1) {
        val command = ProductCommand.IncreaseStocks(
            units = event.orderItems.map {
                ProductCommand.IncreaseStockUnit(it.productId, it.quantity)
            },
        )
        productService.increaseStocks(command)
    }

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
