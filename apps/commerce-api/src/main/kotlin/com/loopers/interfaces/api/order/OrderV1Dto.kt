package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderInfo
import com.loopers.application.order.OrderItemInfo

class OrderV1Dto {
    data class CreateOrderRequest(
        val items: List<OrderItemRequest>,
        val usePoint: Long? = null, // 사용할 포인트 (null이면 사용 안 함)
        val cardType: String? = null, // 카드 결제 시 필수
        val cardNo: String? = null, // 카드 결제 시 필수
        val couponId: Long? = null,
    )

    data class OrderItemRequest(
        val productId: Long,
        val quantity: Int,
    )

    data class OrderResponse(
        val id: Long,
        val memberId: String,
        val status: String,
        val totalAmount: Long,
        val discountAmount: Long,
        val finalAmount: Long,
        val items: List<OrderItemResponse>,
        val createdAt: String,
    ) {
        companion object {
            fun from(orderInfo: OrderInfo): OrderResponse {
                return OrderResponse(
                    id = orderInfo.id,
                    memberId = orderInfo.memberId,
                    status = orderInfo.status.name,
                    totalAmount = orderInfo.totalAmount,
                    discountAmount = orderInfo.discountAmount,
                    finalAmount = orderInfo.finalAmount,
                    items = orderInfo.items.map { OrderItemResponse.from(it) },
                    createdAt = orderInfo.createdAt,
                )
            }
        }
    }

    data class OrderItemResponse(
        val id: Long,
        val productId: Long,
        val productName: String,
        val quantity: Int,
        val price: Long,
        val subtotal: Long,
    ) {
        companion object {
            fun from(orderItemInfo: OrderItemInfo): OrderItemResponse {
                return OrderItemResponse(
                    id = orderItemInfo.id,
                    productId = orderItemInfo.productId,
                    productName = orderItemInfo.productName,
                    quantity = orderItemInfo.quantity,
                    price = orderItemInfo.price,
                    subtotal = orderItemInfo.subtotal,
                )
            }
        }
    }
}

