package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CouponService(
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
) {
    @Transactional
    fun issueCoupon(userId: Long, couponId: Long): IssuedCoupon {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다")

        issuedCouponRepository.findByUserIdAndCouponId(userId, couponId)
            ?.let { throw CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다") }

        val issuedCoupon = IssuedCoupon.issue(userId, coupon)

        return runCatching {
            issuedCouponRepository.save(issuedCoupon)
        }.getOrElse { exception ->
            when (exception) {
                is DataIntegrityViolationException ->
                    throw CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다")

                else -> throw exception
            }
        }
    }

    /**
     * 사용자의 보유 쿠폰 목록을 페이징 조회합니다.
     *
     * @param command 조회 조건 (userId, page, size, sort)
     * @return 보유 쿠폰 정보 목록 (Slice)
     */
    @Transactional(readOnly = true)
    fun findUserCoupons(command: IssuedCouponCommand.FindUserCoupons): Slice<IssuedCouponView> {
        val pageQuery = command.to()
        val slicedIssuedCoupons = issuedCouponRepository.findAllBy(pageQuery)

        val couponIds = slicedIssuedCoupons.content.map { it.couponId }
        val couponsMap = couponRepository.findAllByIds(couponIds).associateBy { it.id }

        return slicedIssuedCoupons.map { issuedCoupon ->
            val coupon = couponsMap[issuedCoupon.couponId]
                ?: throw CoreException(
                    errorType = ErrorType.INTERNAL_ERROR,
                    customMessage = "[couponId = ${issuedCoupon.couponId}] 쿠폰 정의를 찾을 수 없습니다.",
                )

            IssuedCouponView(
                issuedCoupon = issuedCoupon,
                coupon = coupon,
            )
        }
    }

    /**
     * 쿠폰을 사용하고 할인 금액을 반환합니다.
     *
     * @param userId 사용자 ID
     * @param issuedCouponId 발급된 쿠폰 ID
     * @param orderAmount 주문 금액
     * @return 할인 금액
     * @throws CoreException 쿠폰을 보유하지 않았거나 이미 사용된 경우
     */
    @Transactional
    fun useCoupon(userId: Long, issuedCouponId: Long, orderAmount: Money): Money {
        val issuedCoupon = issuedCouponRepository.findById(issuedCouponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "보유하지 않은 쿠폰입니다")

        if (issuedCoupon.userId != userId) {
            throw CoreException(ErrorType.NOT_FOUND, "보유하지 않은 쿠폰입니다")
        }

        val coupon = couponRepository.findById(issuedCoupon.couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰 정의를 찾을 수 없습니다")

        val discountAmount = issuedCoupon.use(coupon, orderAmount)

        issuedCouponRepository.save(issuedCoupon)

        return discountAmount
    }
}
