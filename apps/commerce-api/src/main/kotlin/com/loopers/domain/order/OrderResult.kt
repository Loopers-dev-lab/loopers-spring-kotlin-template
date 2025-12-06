package com.loopers.domain.order

import java.time.ZonedDateTime

object OrderResult {

    data class ListInfo(
        val id: Long,
        val totalAmount: Long,
        val status: OrderStatus,
        val orderedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(
                order: Order,
            ): ListInfo {
                return ListInfo(
                    id = order.id,
                    totalAmount = order.totalAmount,
                    status = order.status,
                    orderedAt = order.createdAt,
                )
            }
        }
    }

    data class DetailInfo(
        val id: Long,
        val userId: Long,
        val totalAmount: Long,
        val status: OrderStatus,
        val items: List<OrderDetailInfo>,
        val orderedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(
                order: Order,
                orderDetails: List<OrderDetail>,
            ): DetailInfo {
                return DetailInfo(
                    id = order.id,
                    userId = order.userId,
                    totalAmount = order.totalAmount,
                    status = order.status,
                    items = orderDetails.map { OrderDetailInfo.from(it) },
                    orderedAt = order.createdAt,
                )
            }
        }

        data class OrderDetailInfo(
            val productId: Long,
            val productName: String,
            val brandId: Long,
            val brandName: String,
            val quantity: Long,
            val price: Long,
        ) {
            companion object {
                fun from(orderDetail: OrderDetail): OrderDetailInfo {
                    return OrderDetailInfo(
                        productId = orderDetail.productId,
                        productName = orderDetail.productName,
                        brandId = orderDetail.brandId,
                        brandName = orderDetail.brandName,
                        quantity = orderDetail.quantity,
                        price = orderDetail.price,
                    )
                }
            }
        }
    }

    data class Create(
        val order: Order,
        val orderDetails: List<OrderDetail>,
    )
}
