package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderCommand
import com.loopers.application.order.OrderItemCommand
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

class OrderDto {
    data class Request(
        @field:NotEmpty(message = "주문 상품은 필수입니다")
        @field:Valid
        val orderItems: List<OrderItemRequest>,
        @field:NotBlank(message = "카드 타입은 필수입니다")
        val cardType: String,
        @field:NotBlank(message = "카드 번호는 필수입니다")
        val cardNo: String,
        val couponId: Long?,
    ) {
        fun toCommand(): OrderCommand = OrderCommand(
            orderItems = orderItems.map { it.toCommand() },
            cardType = cardType,
            cardNo = cardNo,
            couponId = couponId,
        )
    }

    data class OrderItemRequest(
        @field:NotNull(message = "상품 ID는 필수입니다")
        @field:Positive(message = "상품 ID는 양수여야 합니다")
        var productId: Long,
        @field:NotNull(message = "수량은 필수입니다")
        @field:Positive(message = "수량은 양수여야 합니다")
        var quantity: Long,
        @field:NotNull(message = "상품 가격은 필수입니다")
        var productPrice: BigDecimal,
    ) {
        fun toCommand(): OrderItemCommand = OrderItemCommand(
            productId = productId,
            quantity = quantity,
            productPrice = productPrice,
        )
    }

    data class Response(
        val orderId: Long,
        val status: OrderStatus,
        val totalPrice: BigDecimal,
        val orderItems: List<OrderItemResponse>,
    ) {
        companion object {
            fun from(order: OrderModel): Response = Response(
                orderId = order.id,
                status = order.status,
                totalPrice = order.totalPrice.amount,
                orderItems = order.orderItems.map { OrderItemResponse.from(it) },
            )
        }
    }

    data class OrderItemResponse(val productId: Long, val quantity: Long, val productPrice: BigDecimal) {
        companion object {
            fun from(orderItem: com.loopers.domain.order.OrderItemModel): OrderItemResponse = OrderItemResponse(
                productId = orderItem.refProductId,
                quantity = orderItem.quantity,
                productPrice = orderItem.productPrice.amount,
            )
        }
    }
}
