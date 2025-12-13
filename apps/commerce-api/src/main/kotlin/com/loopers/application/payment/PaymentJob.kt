package com.loopers.application.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentFailedEventV1
import com.loopers.domain.payment.PaymentPageQuery
import com.loopers.domain.payment.PaymentPaidEventV1
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentSortType
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgPaymentResult
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.ZonedDateTime

@Component
class PaymentJob(
    private val paymentRepository: PaymentRepository,
    private val pgClient: PgClient,
    private val eventPublisher: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(PaymentJob::class.java)

    /**
     * 이벤트 유실로 PENDING 상태로 남은 결제를 복구한다.
     *
     * @param threshold 이 시각 이전에 생성된 PENDING 결제만 대상
     * @return JobResult 처리 결과
     */
    fun recoverPendingPayments(threshold: ZonedDateTime): JobResult {
        logger.info("recoverPendingPayments start - threshold: {}", threshold)

        var processed = 0
        var skipped = 0

        val query = PaymentPageQuery(
            page = 0,
            size = 100,
            statuses = listOf(PaymentStatus.PENDING),
            sort = PaymentSortType.CREATED_AT_ASC,
            createdBefore = threshold,
        )
        val slice = paymentRepository.findAllBy(query)

        for (payment in slice.content) {
            try {
                processPayment(payment)
                processed++
            } catch (e: Exception) {
                logger.warn("결제 복구 실패 - paymentId: {}", payment.id, e)
                skipped++
            }
        }

        logger.info("recoverPendingPayments end - processed: {}, skipped: {}", processed, skipped)
        return JobResult(processed, skipped)
    }

    private fun processPayment(payment: Payment) {
        val pgResult = pgClient.requestPayment(
            PgPaymentRequest(
                paymentId = payment.id,
                amount = payment.paidAmount,
                cardInfo = payment.cardInfo,
            ),
        )

        val result = payment.initiate(pgResult, Instant.now())

        transactionTemplate.execute {
            paymentRepository.save(payment)
            publishPgPaymentResultEvent(result, payment)
        }
    }

    private fun publishPgPaymentResultEvent(result: PgPaymentResult, payment: Payment) {
        when (result) {
            is PgPaymentResult.InProgress -> {}
            is PgPaymentResult.NotRequired -> {
                eventPublisher.publishEvent(
                    PaymentPaidEventV1(
                        paymentId = payment.id,
                        orderId = payment.orderId,
                    ),
                )
            }
            is PgPaymentResult.Failed -> {
                eventPublisher.publishEvent(
                    PaymentFailedEventV1(
                        paymentId = payment.id,
                        orderId = payment.orderId,
                        userId = payment.userId,
                        usedPoint = payment.usedPoint,
                        issuedCouponId = payment.issuedCouponId,
                    ),
                )
            }
        }
    }
}

data class JobResult(
    val processed: Int,
    val skipped: Int,
)
