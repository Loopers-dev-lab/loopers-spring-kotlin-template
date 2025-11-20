package com.loopers.domain.coupon

class IssuedCouponCommand {
    data class FindUserCoupons(
        val userId: Long,
        val page: Int? = null,
        val size: Int? = null,
        val sort: IssuedCouponSortType? = null,
    ) {
        fun to(): IssuedCouponPageQuery {
            return IssuedCouponPageQuery.of(
                userId = userId,
                page = page,
                size = size,
                sort = sort,
            )
        }
    }
}
