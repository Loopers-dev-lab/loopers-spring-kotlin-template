package com.loopers.application.coupon

import com.loopers.domain.coupon.IssuedCouponCommand
import com.loopers.domain.coupon.IssuedCouponSortType

class IssuedCouponCriteria {
    data class FindUserCoupons(
        val userId: Long,
        val page: Int? = null,
        val size: Int? = null,
        val sort: IssuedCouponSortType? = null,
    ) {
        fun to(): IssuedCouponCommand.FindUserCoupons {
            return IssuedCouponCommand.FindUserCoupons(
                userId = userId,
                page = page,
                size = size,
                sort = sort,
            )
        }
    }
}
