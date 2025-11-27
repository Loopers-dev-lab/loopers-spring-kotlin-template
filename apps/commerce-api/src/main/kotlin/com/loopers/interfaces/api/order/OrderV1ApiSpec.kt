package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Order V1 API", description = "Loopers Order API 입니다.")
interface OrderV1ApiSpec {
    @Operation(
        summary = "주문 생성",
        description = """
            주문을 생성합니다.
            - 재고 차감 → 쿠폰 할인 적용 → 포인트 차감 → 결제 처리 순서로 진행됩니다.
            - 쿠폰 사용은 선택사항이며, issuedCouponId를 전달하면 해당 쿠폰이 적용됩니다.
            - 사용 포인트(usePoint)는 쿠폰 할인 후 최종 결제 금액과 정확히 일치해야 합니다.
        """,
    )
    fun placeOrder(
        @Parameter(
            name = "X-USER-ID",
            description = "요청자의 유저 ID",
            required = true,
            `in` = ParameterIn.HEADER,
        )
        userId: Long,
        request: OrderV1Request.PlaceOrder,
    ): ApiResponse<OrderV1Response.PlaceOrder>
}
