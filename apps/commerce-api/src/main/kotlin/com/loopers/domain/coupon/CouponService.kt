package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val couponIssueRepository: CouponIssueRepository,
) {

    @Transactional
    fun applyCoupon(userId: Long, couponId: Long?, totalAmount: Long): Long {
        if (couponId == null) {
            return 0
        }

        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다: $couponId")
        val couponIssue = couponIssueRepository.findBy(userId, couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "사용자가 발급 받은 적 없는 쿠폰입니다. userId: $userId, couponId: $couponId")

        couponIssue.use()
        return coupon.calculateDiscount(totalAmount)
    }

    @Transactional
    fun rollback(userId: Long, couponId: Long?) {
        if (couponId == null) {
            return
        }

        val couponIssue = couponIssueRepository.findBy(userId, couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "사용자가 발급 받은 적 없는 쿠폰입니다. userId: $userId, couponId: $couponId")

        couponIssue.rollback()
    }
}
