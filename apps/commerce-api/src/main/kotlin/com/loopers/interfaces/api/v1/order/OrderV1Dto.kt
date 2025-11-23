package com.loopers.interfaces.api.v1.order

import com.loopers.application.order.OrderResult
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime

class OrderV1Dto {
    @Schema(description = "주문 생성 요청")
    data class CreateOrderRequest(
        @Schema(description = "주문 상품 목록", required = true)
        val items: List<OrderItemRequest>,
        @Schema(description = "쿠폰 ID", required = false)
        val couponId: Long?,
    ) {
        fun toCommand(): List<OrderCommand.OrderDetailCommand> = items.map {
            OrderCommand.OrderDetailCommand(
                productId = it.productId,
                quantity = it.quantity,
            )
        }
    }

    @Schema(description = "주문 상품 요청")
    data class OrderItemRequest(
        @Schema(description = "상품 ID", required = true, example = "1")
        val productId: Long,
        @Schema(description = "주문 수량", required = true, example = "2")
        val quantity: Long,
    )

    @Schema(description = "주문 생성 응답")
    data class CreateOrderResponse(
        @Schema(description = "주문 생성 성공 메시지")
        val message: String = "주문이 성공적으로 생성되었습니다.",
    )

    @Schema(description = "주문 목록 응답")
    data class OrderListResponse(
        @Schema(description = "주문 목록")
        val items: List<OrderInfo>,
    ) {
        companion object {
            fun from(
                content: List<OrderResult.ListInfo>,
            ): OrderListResponse = OrderListResponse(
                items = content.map { OrderInfo.from(it) },
            )
        }
    }

    @Schema(description = "주문 정보")
    data class OrderInfo(
        @Schema(description = "주문 ID")
        val id: Long,
        @Schema(description = "총 주문 금액")
        val totalAmount: Long,
        @Schema(description = "주문 상태")
        val status: OrderStatus,
        @Schema(description = "주문 일시")
        val orderedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: OrderResult.ListInfo): OrderInfo = OrderInfo(
                id = info.id,
                totalAmount = info.totalAmount,
                status = info.status,
                orderedAt = info.orderedAt,
            )
        }
    }

    @Schema(description = "주문 상세 응답")
    data class OrderDetailResponse(
        @Schema(description = "주문 ID")
        val id: Long,
        @Schema(description = "사용자 ID")
        val userId: Long,
        @Schema(description = "총 주문 금액")
        val totalAmount: Long,
        @Schema(description = "주문 상태")
        val status: OrderStatus,
        @Schema(description = "주문 상품 목록")
        val items: List<OrderDetailInfo>,
        @Schema(description = "주문 일시")
        val orderedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: OrderResult.DetailInfo): OrderDetailResponse = OrderDetailResponse(
                id = info.id,
                userId = info.userId,
                totalAmount = info.totalAmount,
                status = info.status,
                items = info.items.map { OrderDetailInfo.from(it) },
                orderedAt = info.orderedAt,
            )
        }
    }

    @Schema(description = "주문 상품 상세 정보")
    data class OrderDetailInfo(
        @Schema(description = "상품 ID")
        val productId: Long,
        @Schema(description = "상품명")
        val productName: String,
        @Schema(description = "브랜드 ID")
        val brandId: Long,
        @Schema(description = "브랜드명")
        val brandName: String,
        @Schema(description = "주문 수량")
        val quantity: Long,
        @Schema(description = "상품 가격")
        val price: Long,
    ) {
        companion object {
            fun from(info: OrderResult.DetailInfo.OrderDetailInfo): OrderDetailInfo = OrderDetailInfo(
                productId = info.productId,
                productName = info.productName,
                brandId = info.brandId,
                brandName = info.brandName,
                quantity = info.quantity,
                price = info.price,
            )
        }
    }
}
