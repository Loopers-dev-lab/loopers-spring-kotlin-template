package com.loopers.interfaces.event.product

import com.loopers.domain.like.LikeCanceledEventV1
import com.loopers.domain.like.LikeCreatedEventV1
import com.loopers.domain.order.OrderCanceledEventV1
import com.loopers.domain.order.OrderCreatedEventV1
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProductEventListener(
    private val productService: ProductService,
) {
    private val logger = LoggerFactory.getLogger(ProductEventListener::class.java)

    @EventListener()
    fun onOrderCreated(event: OrderCreatedEventV1) {
        val command = ProductCommand.DecreaseStocks(
            units = event.orderItems.map {
                ProductCommand.DecreaseStockUnit(it.productId, it.quantity)
            },
        )
        productService.decreaseStocks(command)
    }

    @EventListener()
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
        logger.info("[LikeCreatedEventV1] start - userId: ${event.userId}, productId: ${event.productId}")
        try {
            productService.increaseProductLikeCount(event.productId)
            logger.info("[LikeCreatedEventV1] success - userId: ${event.userId}, productId: ${event.productId}")
        } catch (e: Exception) {
            logger.error("[LikeCreatedEventV1] failed - userId: ${event.userId}, productId: ${event.productId}", e)
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onLikeCanceled(event: LikeCanceledEventV1) {
        logger.info("[LikeCanceledEventV1] start - userId: ${event.userId}, productId: ${event.productId}")
        try {
            productService.decreaseProductLikeCount(event.productId)
            logger.info("[LikeCanceledEventV1] success - userId: ${event.userId}, productId: ${event.productId}")
        } catch (e: Exception) {
            logger.error("[LikeCanceledEventV1] failed - userId: ${event.userId}, productId: ${event.productId}", e)
        }
    }
}
