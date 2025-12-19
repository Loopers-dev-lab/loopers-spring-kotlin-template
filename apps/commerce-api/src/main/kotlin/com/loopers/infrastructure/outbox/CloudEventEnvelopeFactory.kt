package com.loopers.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
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
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.support.event.DomainEvent
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CloudEventEnvelopeFactory(
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val SOURCE = "commerce-api"
    }

    fun create(event: DomainEvent): CloudEventEnvelope? {
        val metadata = resolveMetadata(event) ?: return null
        return CloudEventEnvelope(
            id = UUID.randomUUID().toString(),
            type = metadata.type,
            source = SOURCE,
            aggregateType = metadata.aggregateType,
            aggregateId = metadata.aggregateId,
            time = event.occurredAt,
            payload = objectMapper.writeValueAsString(event),
        )
    }

    private fun resolveMetadata(event: DomainEvent): EventMetadata? = when (event) {
        is OrderCreatedEventV1 -> EventMetadata(
            type = "loopers.order.created.v1",
            aggregateType = "Order",
            aggregateId = event.orderId.toString(),
        )
        is OrderCanceledEventV1 -> EventMetadata(
            type = "loopers.order.canceled.v1",
            aggregateType = "Order",
            aggregateId = event.orderId.toString(),
        )
        is OrderPaidEventV1 -> EventMetadata(
            type = "loopers.order.paid.v1",
            aggregateType = "Order",
            aggregateId = event.orderId.toString(),
        )
        is PaymentCreatedEventV1 -> EventMetadata(
            type = "loopers.payment.created.v1",
            aggregateType = "Payment",
            aggregateId = event.paymentId.toString(),
        )
        is PaymentPaidEventV1 -> EventMetadata(
            type = "loopers.payment.paid.v1",
            aggregateType = "Payment",
            aggregateId = event.paymentId.toString(),
        )
        is PaymentFailedEventV1 -> EventMetadata(
            type = "loopers.payment.failed.v1",
            aggregateType = "Payment",
            aggregateId = event.paymentId.toString(),
        )
        is LikeCreatedEventV1 -> EventMetadata(
            type = "loopers.like.created.v1",
            aggregateType = "Like",
            aggregateId = event.productId.toString(),
        )
        is LikeCanceledEventV1 -> EventMetadata(
            type = "loopers.like.canceled.v1",
            aggregateType = "Like",
            aggregateId = event.productId.toString(),
        )
        is ProductViewedEventV1 -> EventMetadata(
            type = "loopers.product.viewed.v1",
            aggregateType = "Product",
            aggregateId = event.productId.toString(),
        )
        is StockDepletedEventV1 -> EventMetadata(
            type = "loopers.stock.depleted.v1",
            aggregateType = "Stock",
            aggregateId = event.productId.toString(),
        )
        else -> null
    }

    private data class EventMetadata(
        val type: String,
        val aggregateType: String,
        val aggregateId: String,
    )
}
