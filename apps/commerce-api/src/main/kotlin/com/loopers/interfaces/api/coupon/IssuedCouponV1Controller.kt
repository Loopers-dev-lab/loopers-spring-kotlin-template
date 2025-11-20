package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.application.coupon.IssuedCouponCriteria
import com.loopers.domain.coupon.IssuedCouponSortType
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/issued-coupons")
class IssuedCouponV1Controller(
    private val couponFacade: CouponFacade,
) {

    @PostMapping("/{couponId}")
    fun issueCoupon(
        @RequestHeader("X-USER-ID") userId: Long,
        @PathVariable couponId: Long,
    ): ApiResponse<IssuedCouponV1Response.Issue> {
        return couponFacade.issueCoupon(userId, couponId)
            .let { IssuedCouponV1Response.Issue.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping
    fun getIssuedCoupons(
        @RequestHeader("X-USER-ID") userId: Long,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) sort: IssuedCouponSortType?,
    ): ApiResponse<IssuedCouponV1Response.UserCoupons> {
        val criteria = IssuedCouponCriteria.FindUserCoupons(
            userId = userId,
            page = page,
            size = size,
            sort = sort,
        )
        val slicedCoupons = couponFacade.findUserCoupons(criteria)
        return IssuedCouponV1Response.UserCoupons.from(slicedCoupons)
            .let { ApiResponse.success(it) }
    }
}
