package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderCreateInfo
import com.loopers.application.order.OrderDetailInfo
import com.loopers.application.order.OrderItemInfo
import com.loopers.application.order.OrderListInfo
import java.math.BigDecimal
import java.time.LocalDateTime
import com.loopers.application.order.OrderCreateRequest as AppOrderCreateRequest
import com.loopers.application.order.OrderItemRequest as AppOrderItemRequest

class OrderV1Dto {
    data class OrderCreateRequest(val items: List<OrderItemRequest>) {
        fun toApplicationRequest(): AppOrderCreateRequest = AppOrderCreateRequest(
            items = items.map {
                AppOrderItemRequest(
                    productId = it.productId,
                    quantity = it.quantity,
                )
            },
        )
    }

    data class OrderItemRequest(val productId: Long, val quantity: Int)

    data class OrderCreateResponse(
        val orderId: Long,
        val userId: Long,
        val totalAmount: BigDecimal,
        val currency: String,
        val status: String,
    ) {
        companion object {
            fun from(
                info: OrderCreateInfo,
            ): OrderCreateResponse = OrderCreateResponse(
                orderId = info.orderId,
                userId = info.userId,
                totalAmount = info.totalAmount,
                currency = info.currency,
                status = info.status,
            )
        }
    }

    data class OrderListResponse(
        val orderId: Long,
        val totalAmount: BigDecimal,
        val currency: String,
        val status: String,
        val orderedAt: LocalDateTime,
    ) {
        companion object {
            fun from(
                info: OrderListInfo,
            ): OrderListResponse = OrderListResponse(
                orderId = info.orderId,
                totalAmount = info.totalAmount,
                currency = info.currency,
                status = info.status,
                orderedAt = info.orderedAt,
            )
        }
    }

    data class OrderDetailResponse(
        val orderId: Long,
        val userId: Long,
        val totalAmount: BigDecimal,
        val currency: String,
        val status: String,
        val items: List<OrderItemResponse>,
        val orderedAt: LocalDateTime,
    ) {
        companion object {
            fun from(
                info: OrderDetailInfo,
            ): OrderDetailResponse = OrderDetailResponse(
                orderId = info.orderId,
                userId = info.userId,
                totalAmount = info.totalAmount,
                currency = info.currency,
                status = info.status,
                items = info.items.map { OrderItemResponse.from(it) },
                orderedAt = info.orderedAt,
            )
        }
    }

    data class OrderItemResponse(
        val productId: Long,
        val productName: String,
        val brandId: Long,
        val brandName: String,
        val quantity: Int,
        val priceAtOrder: BigDecimal,
        val currency: String,
    ) {
        companion object {
            fun from(
                info: OrderItemInfo,
            ): OrderItemResponse = OrderItemResponse(
                productId = info.productId,
                productName = info.productName,
                brandId = info.brandId,
                brandName = info.brandName,
                quantity = info.quantity,
                priceAtOrder = info.priceAtOrder,
                currency = info.currency,
            )
        }
    }
}
