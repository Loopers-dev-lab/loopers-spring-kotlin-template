package com.loopers.interfaces.api.coupon

import com.loopers.domain.coupon.IssuedCouponSortType
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Issued Coupon V1 API", description = "Loopers Issued Coupon API 입니다.")
interface IssuedCouponV1ApiSpec {
    @Operation(
        summary = "쿠폰 발급",
        description = """
            사용자에게 쿠폰을 발급합니다.
            - 이미 발급받은 쿠폰은 중복 발급되지 않습니다.
            - 발급된 쿠폰은 주문 시 할인 적용에 사용할 수 있습니다.
        """,
    )
    fun issueCoupon(
        @Parameter(
            name = "X-USER-ID",
            description = "요청자의 유저 ID",
            required = true,
            `in` = ParameterIn.HEADER,
        )
        userId: Long,
        @Parameter(
            name = "couponId",
            description = "발급받을 쿠폰 ID",
            required = true,
            `in` = ParameterIn.PATH,
        )
        couponId: Long,
    ): ApiResponse<IssuedCouponV1Response.Issue>

    @Operation(
        summary = "보유 쿠폰 목록 조회",
        description = """
            사용자가 보유한 쿠폰 목록을 페이지네이션으로 조회합니다.
            - 사용 가능한 쿠폰과 이미 사용된 쿠폰 모두 조회됩니다.
            - 정렬 옵션으로 최신순(LATEST) 또는 오래된순(OLDEST)을 선택할 수 있습니다.
        """,
    )
    fun getIssuedCoupons(
        @Parameter(
            name = "X-USER-ID",
            description = "요청자의 유저 ID",
            required = true,
            `in` = ParameterIn.HEADER,
        )
        userId: Long,
        @Parameter(
            name = "page",
            description = "페이지 번호 (0부터 시작) (선택, 기본값: 0)",
            required = false,
            `in` = ParameterIn.QUERY,
        )
        page: Int?,
        @Parameter(
            name = "size",
            description = "페이지 크기 (1~100) (선택, 기본값: 20)",
            required = false,
            `in` = ParameterIn.QUERY,
        )
        size: Int?,
        @Parameter(
            name = "sort",
            description = "정렬 방식 (LATEST, OLDEST) (선택, 기본값: LATEST)",
            required = false,
            `in` = ParameterIn.QUERY,
        )
        sort: IssuedCouponSortType?,
    ): ApiResponse<IssuedCouponV1Response.UserCoupons>
}
