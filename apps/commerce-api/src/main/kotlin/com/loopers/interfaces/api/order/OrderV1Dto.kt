package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderCreateInfo
import com.loopers.application.order.OrderCreateRequest as AppOrderCreateRequest
import com.loopers.application.order.OrderDetailInfo
import com.loopers.application.order.OrderItemRequest as AppOrderItemRequest
import com.loopers.application.order.OrderListInfo
import java.math.BigDecimal
import java.time.ZonedDateTime

class OrderV1Dto {
    data class OrderCreateRequest(
        val items: List<OrderItemRequest>
    ) {
        fun toApplicationRequest(): AppOrderCreateRequest {
            return AppOrderCreateRequest(
                items = items.map {
                    AppOrderItemRequest(
                        productId = it.productId,
                        quantity = it.quantity
                    )
                }
            )
        }
    }

    data class OrderItemRequest(
        val productId: Long,
        val quantity: Int
    )

    data class OrderCreateResponse(
        val orderId: Long,
        val userId: Long,
        val totalAmount: BigDecimal,
        val currency: String,
        val status: String
    ) {
        companion object {
            fun from(info: OrderCreateInfo): OrderCreateResponse {
                return OrderCreateResponse(
                    orderId = info.orderId,
                    userId = info.userId,
                    totalAmount = info.totalAmount,
                    currency = info.currency,
                    status = info.status
                )
            }
        }
    }

    data class OrderListResponse(
        val orderId: Long,
        val totalAmount: BigDecimal,
        val currency: String,
        val status: String,
        val orderedAt: ZonedDateTime
    ) {
        companion object {
            fun from(info: OrderListInfo): OrderListResponse {
                return OrderListResponse(
                    orderId = info.orderId,
                    totalAmount = info.totalAmount,
                    currency = info.currency,
                    status = info.status,
                    orderedAt = info.orderedAt
                )
            }
        }
    }

    data class OrderDetailResponse(
        val orderId: Long,
        val userId: Long,
        val totalAmount: BigDecimal,
        val currency: String,
        val status: String,
        val items: List<OrderItemResponse>,
        val orderedAt: ZonedDateTime
    ) {
        companion object {
            fun from(info: OrderDetailInfo): OrderDetailResponse {
                return OrderDetailResponse(
                    orderId = info.orderId,
                    userId = info.userId,
                    totalAmount = info.totalAmount,
                    currency = info.currency,
                    status = info.status,
                    items = info.items.map { OrderItemResponse.from(it) },
                    orderedAt = info.orderedAt
                )
            }
        }
    }

    data class OrderItemResponse(
        val productId: Long,
        val productName: String,
        val brandId: Long,
        val brandName: String,
        val quantity: Int,
        val priceAtOrder: BigDecimal,
        val currency: String
    ) {
        companion object {
            fun from(info: com.loopers.application.order.OrderItemInfo): OrderItemResponse {
                return OrderItemResponse(
                    productId = info.productId,
                    productName = info.productName,
                    brandId = info.brandId,
                    brandName = info.brandName,
                    quantity = info.quantity,
                    priceAtOrder = info.priceAtOrder,
                    currency = info.currency
                )
            }
        }
    }
}
