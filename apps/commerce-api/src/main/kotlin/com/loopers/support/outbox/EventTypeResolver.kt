package com.loopers.support.outbox

import com.loopers.domain.like.LikeCanceledEventV1
import com.loopers.domain.like.LikeCreatedEventV1
import com.loopers.domain.order.OrderCanceledEventV1
import com.loopers.domain.order.OrderCreatedEventV1
import com.loopers.domain.order.OrderPaidEventV1
import com.loopers.domain.payment.PaymentCreatedEventV1
import com.loopers.domain.payment.PaymentFailedEventV1
import com.loopers.domain.payment.PaymentPaidEventV1
import com.loopers.domain.product.ProductViewedEventV1
import com.loopers.domain.product.StockDepletedEventV1
import com.loopers.support.event.DomainEvent

object EventTypeResolver {
    fun resolve(event: DomainEvent): String = when (event) {
        is OrderCreatedEventV1 -> "loopers.order.created.v1"
        is OrderCanceledEventV1 -> "loopers.order.canceled.v1"
        is OrderPaidEventV1 -> "loopers.order.paid.v1"
        is PaymentCreatedEventV1 -> "loopers.payment.created.v1"
        is PaymentPaidEventV1 -> "loopers.payment.paid.v1"
        is PaymentFailedEventV1 -> "loopers.payment.failed.v1"
        is LikeCreatedEventV1 -> "loopers.like.created.v1"
        is LikeCanceledEventV1 -> "loopers.like.canceled.v1"
        is ProductViewedEventV1 -> "loopers.product.viewed.v1"
        is StockDepletedEventV1 -> "loopers.stock.depleted.v1"
        else -> throw IllegalArgumentException("Unknown event type: ${event::class.simpleName}")
    }
}
