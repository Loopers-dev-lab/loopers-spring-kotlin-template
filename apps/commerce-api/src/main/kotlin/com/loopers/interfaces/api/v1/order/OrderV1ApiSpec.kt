package com.loopers.interfaces.api.v1.order

import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.dto.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable

@Tag(name = "Order V1 API", description = "주문 API")
interface OrderV1ApiSpec {
    @Operation(
        summary = "주문 생성",
        description = "여러 상품을 포함한 주문을 생성합니다. 재고 차감, 포인트 차감이 트랜잭션으로 처리됩니다.",
    )
    fun placeOrder(
        @Parameter(description = "사용자 ID (X-USER-ID 헤더)", required = true)
        userId: String,
        request: OrderV1Dto.CreateOrderRequest,
    ): ApiResponse<OrderV1Dto.CreateOrderResponse>

    @Operation(
        summary = "주문 목록 조회",
        description = "사용자의 주문 목록을 최신순으로 조회합니다.",
    )
    fun getOrders(
        @Parameter(description = "사용자 ID (X-USER-ID 헤더)", required = true)
        userId: String,
        @Parameter(description = "페이지 번호 (0부터 시작)", schema = Schema(defaultValue = "0"))
        pageable: Pageable,
    ): ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>>

    @Operation(
        summary = "주문 상세 조회",
        description = "특정 주문의 상세 정보를 조회합니다.",
    )
    fun getOrder(
        @Parameter(description = "사용자 ID (X-USER-ID 헤더)", required = true)
        userId: String,
        @Schema(description = "주문 ID")
        orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderDetailResponse>
}
