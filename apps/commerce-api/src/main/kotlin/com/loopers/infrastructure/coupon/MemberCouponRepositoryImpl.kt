package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.MemberCoupon
import com.loopers.domain.coupon.MemberCouponRepository
import org.springframework.stereotype.Component

@Component
class MemberCouponRepositoryImpl(
    private val memberCouponJpaRepository: MemberCouponJpaRepository,
) : MemberCouponRepository {

    override fun findByMemberIdAndCouponId(
        memberId: Long,
        couponId: Long,
    ): MemberCoupon? {
        return memberCouponJpaRepository.findByMemberIdAndCouponIdWithLock(memberId.toString(), couponId)
    }

    override fun findByMemberIdAndCouponIdWithLock(memberId: String, couponId: Long): MemberCoupon? {
        return memberCouponJpaRepository.findByMemberIdAndCouponIdWithLock(memberId, couponId)
    }

    override fun save(memberCoupon: MemberCoupon): MemberCoupon {
        return memberCouponJpaRepository.save(memberCoupon)
    }
}
