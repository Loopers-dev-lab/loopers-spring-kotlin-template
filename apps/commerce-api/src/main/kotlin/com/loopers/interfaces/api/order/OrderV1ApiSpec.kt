package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page

@Tag(name = "Order V1 API", description = "주문 관련 API 입니다.")
interface OrderV1ApiSpec {
    @Operation(
        summary = "주문 생성",
        description = """
            주문을 생성합니다.
            - 쿠폰 할인과 포인트를 사용할 수 있습니다.
            - 최종 금액이 0원이면 포인트 전액 결제로 완료됩니다.
            - 최종 금액이 0원 이상이면 카드 결제가 필요합니다.
        """,
    )
    fun createOrder(
        @Schema(name = "회원 ID", description = "주문하는 회원의 ID")
        memberId: String,
        @Schema(name = "주문 정보", description = "주문할 상품 및 결제 정보")
        request: OrderV1Dto.CreateOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse>

    @Operation(
        summary = "주문 목록 조회",
        description = "회원의 주문 목록을 조회합니다.",
    )
    fun getOrders(
        @Schema(name = "회원 ID", description = "조회할 회원의 ID")
        memberId: String,
        @Schema(name = "페이지 번호", description = "페이지 번호")
        page: Int,
        @Schema(name = "페이지 크기", description = "페이지 크기")
        size: Int,
    ): ApiResponse<Page<OrderV1Dto.OrderResponse>>

    @Operation(
        summary = "주문 상세 조회",
        description = "ID로 주문 상세 정보를 조회합니다.",
    )
    fun getOrder(
        @Schema(name = "주문 ID", description = "조회할 주문의 ID")
        orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse>
}

