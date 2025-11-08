package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page

@Tag(name = "Order V1 API", description = "주문 API")
interface OrderV1ApiSpec {
    @Operation(
        summary = "주문 생성",
        description = "새로운 주문을 생성합니다. 재고와 포인트를 검증하고 차감합니다.",
    )
    fun createOrder(
        @Parameter(description = "사용자 ID", required = true)
        userId: Long,
        request: OrderV1Dto.OrderCreateRequest,
    ): ApiResponse<OrderV1Dto.OrderCreateResponse>

    @Operation(
        summary = "주문 목록 조회",
        description = "사용자의 주문 목록을 조회합니다.",
    )
    fun getOrders(
        @Parameter(description = "사용자 ID", required = true)
        userId: Long,
        @Parameter(description = "페이지 번호", schema = Schema(defaultValue = "0"))
        page: Int,
        @Parameter(description = "페이지 크기", schema = Schema(defaultValue = "20"))
        size: Int,
    ): ApiResponse<Page<OrderV1Dto.OrderListResponse>>

    @Operation(
        summary = "주문 상세 조회",
        description = "주문 ID로 주문 상세 정보를 조회합니다. 본인의 주문만 조회 가능합니다.",
    )
    fun getOrderDetail(
        @Parameter(description = "사용자 ID", required = true)
        userId: Long,
        @Schema(description = "주문 ID")
        orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderDetailResponse>
}
