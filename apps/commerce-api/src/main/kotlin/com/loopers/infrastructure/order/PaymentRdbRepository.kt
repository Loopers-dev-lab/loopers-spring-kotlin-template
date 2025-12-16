package com.loopers.infrastructure.order

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentPageQuery
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentSortType
import com.loopers.domain.payment.QPayment
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class PaymentRdbRepository(
    private val paymentJpaRepository: PaymentJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : PaymentRepository {
    override fun findById(id: Long): Payment? {
        return paymentJpaRepository.findByIdOrNull(id)
    }

    override fun findByOrderId(orderId: Long): Payment? {
        return paymentJpaRepository.findByOrderId(orderId)
    }

    override fun findByExternalPaymentKey(key: String): Payment? {
        return paymentJpaRepository.findByExternalPaymentKey(key)
    }

    @Transactional(readOnly = true)
    override fun findAllBy(query: PaymentPageQuery): Slice<Payment> {
        val qPayment = QPayment.payment

        val baseQuery = queryFactory
            .selectFrom(qPayment)
            .where(
                query.statuses.takeIf { it.isNotEmpty() }?.let { qPayment.status.`in`(it) },
                query.createdBefore?.let { qPayment.createdAt.lt(it) },
            )
            .orderBy(*getOrderSpecifiers(query.sort, qPayment))
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
        sort: PaymentSortType,
        qPayment: QPayment,
    ): Array<OrderSpecifier<*>> {
        return when (sort) {
            PaymentSortType.CREATED_AT_ASC -> arrayOf(
                qPayment.createdAt.asc(),
                qPayment.id.asc(),
            )

            PaymentSortType.CREATED_AT_DESC -> arrayOf(
                qPayment.createdAt.desc(),
                qPayment.id.desc(),
            )
        }
    }

    override fun save(payment: Payment): Payment {
        return paymentJpaRepository.saveAndFlush(payment)
    }
}
