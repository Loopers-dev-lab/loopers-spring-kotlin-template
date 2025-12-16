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
        logger.info("onLikeCreated start - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}")
        try {
            productService.increaseProductLikeCount(event.productId)
            logger.info("onLikeCreated success - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}")
        } catch (e: Exception) {
            logger.error("onLikeCreated failed - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}", e)
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onLikeCanceled(event: LikeCanceledEventV1) {
        logger.info("onLikeCanceled start - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}")
        try {
            productService.decreaseProductLikeCount(event.productId)
            logger.info("onLikeCanceled success - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}")
        } catch (e: Exception) {
            logger.error("onLikeCanceled failed - eventType: ${event.eventType}, aggregateId: ${event.aggregateId}", e)
        }
    }
}
