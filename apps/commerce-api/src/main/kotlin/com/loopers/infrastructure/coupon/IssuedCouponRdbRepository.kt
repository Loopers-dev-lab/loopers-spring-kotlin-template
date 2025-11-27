package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponPageQuery
import com.loopers.domain.coupon.IssuedCouponRepository
import com.loopers.domain.coupon.IssuedCouponSortType
import com.loopers.domain.coupon.QIssuedCoupon
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class IssuedCouponRdbRepository(
    private val queryFactory: JPAQueryFactory,
    private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
) : IssuedCouponRepository {

    @Transactional(readOnly = true)
    override fun findAllBy(query: IssuedCouponPageQuery): Slice<IssuedCoupon> {
        val qIssuedCoupon = QIssuedCoupon.issuedCoupon

        val baseQuery = queryFactory
            .selectFrom(qIssuedCoupon)
            .where(qIssuedCoupon.userId.eq(query.userId))
            .orderBy(*getOrderSpecifiers(query.sort, qIssuedCoupon))
            .offset((query.page * query.size).toLong())
            .limit((query.size + 1).toLong())

        val results = baseQuery.fetch()

        val hasNext = results.size > query.size
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(
            content,
            PageRequest.of(query.page, query.size),
            hasNext,
        )
    }

    private fun getOrderSpecifiers(
        sort: IssuedCouponSortType,
        qIssuedCoupon: QIssuedCoupon,
    ): Array<OrderSpecifier<*>> {
        return when (sort) {
            IssuedCouponSortType.LATEST -> arrayOf(
                qIssuedCoupon.id.desc(),
            )

            IssuedCouponSortType.OLDEST -> arrayOf(
                qIssuedCoupon.id.asc(),
            )
        }
    }

    @Transactional(readOnly = true)
    override fun findByUserIdAndCouponId(userId: Long, couponId: Long): IssuedCoupon? {
        return issuedCouponJpaRepository.findByUserIdAndCouponId(userId, couponId)
    }

    @Transactional(readOnly = true)
    override fun findById(id: Long): IssuedCoupon? {
        return issuedCouponJpaRepository.findByIdOrNull(id)
    }

    @Transactional
    override fun save(issuedCoupon: IssuedCoupon): IssuedCoupon {
        return issuedCouponJpaRepository.saveAndFlush(issuedCoupon)
    }
}
