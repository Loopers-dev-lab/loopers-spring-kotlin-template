package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Slice
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Component
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val pgClient: PgClient,
    private val eventPublisher: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
) {
    /**
     * 결제를 생성합니다.
     * paidAmount는 totalAmount - usedPoint - couponDiscount로 자동 계산됩니다.
     * - paidAmount가 0이면 (포인트+쿠폰으로 전액 결제) → PAID
     * - paidAmount가 0보다 크면 (PG 결제 필요) → PENDING
     *
     * @param command 결제 생성 커맨드
     * @return 생성된 Payment
     */
    @Transactional
    fun create(command: PaymentCommand.Create): Payment {
        val payment = Payment.create(
            userId = command.userId,
            orderId = command.orderId,
            totalAmount = command.totalAmount,
            usedPoint = command.usedPoint,
            issuedCouponId = command.issuedCouponId,
            couponDiscount = command.couponDiscount,
            cardInfo = command.cardInfo,
        )

        val savedPayment = paymentRepository.save(payment)

        eventPublisher.publishEvent(
            PaymentCreatedEventV1(paymentId = savedPayment.id),
        )

        return savedPayment
    }

    /**
     * 결제 목록을 조회합니다.
     * pagination과 동적 조건(status)을 지원합니다.
     *
     * @param command 조회 조건 (page, size, sort, statuses)
     * @return Slice<Payment> 결제 목록
     */
    @Transactional(readOnly = true)
    fun findPayments(command: PaymentCommand.FindPayments): Slice<Payment> {
        val query = command.toQuery()
        return paymentRepository.findAllBy(query)
    }

    /**
     * PG 콜백을 처리합니다.
     * orderId로 결제를 조회하고, externalPaymentKey로 PG 트랜잭션을 조회하여 결제를 확정합니다.
     *
     * @param orderId 주문 ID
     * @param externalPaymentKey PG 외부 결제 키
     * @param currentTime 현재 시각 (타임아웃 판단용)
     * @return ConfirmResult - 결제 확정 결과
     * @throws CoreException 결제를 찾을 수 없는 경우
     */
    @Transactional
    fun processCallback(
        orderId: Long,
        externalPaymentKey: String,
        currentTime: Instant = Instant.now(),
    ): ConfirmResult {
        val payment = paymentRepository.findByOrderId(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")

        val transaction = pgClient.findTransaction(externalPaymentKey)
        val result = payment.confirmPayment(listOf(transaction), currentTime)
        paymentRepository.save(payment)

        when (result) {
            is ConfirmResult.Paid -> {
                eventPublisher.publishEvent(
                    PaymentPaidEventV1(
                        paymentId = payment.id,
                        orderId = payment.orderId,
                    ),
                )
            }

            is ConfirmResult.Failed -> {
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

            is ConfirmResult.StillInProgress -> {
                /* No event, still waiting */
            }
        }

        return result
    }

    /**
     * IN_PROGRESS 상태의 결제를 처리합니다.
     * 스케줄러에서 호출됩니다. paymentId로 PG 트랜잭션을 조회하여 결제를 확정합니다.
     *
     * @param paymentId 결제 ID
     * @param currentTime 현재 시각 (타임아웃 판단용)
     * @return ConfirmResult - 결제 확정 결과
     * @throws CoreException 결제를 찾을 수 없는 경우
     */
    @Transactional
    fun processInProgressPayment(
        paymentId: Long,
        currentTime: Instant = Instant.now(),
    ): ConfirmResult {
        val payment = paymentRepository.findById(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")

        val transactions = payment.externalPaymentKey?.let { key ->
            listOf(pgClient.findTransaction(key))
        } ?: pgClient.findTransactionsByPaymentId(payment.id)

        val result = payment.confirmPayment(transactions, currentTime)
        paymentRepository.save(payment)

        when (result) {
            is ConfirmResult.Paid -> {
                eventPublisher.publishEvent(
                    PaymentPaidEventV1(
                        paymentId = payment.id,
                        orderId = payment.orderId,
                    ),
                )
            }

            is ConfirmResult.Failed -> {
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

            is ConfirmResult.StillInProgress -> {
                /* No event, still waiting */
            }
        }

        return result
    }

    /**
     * PG 결제를 요청하고 결제 상태를 업데이트합니다.
     * 이 메서드는 트랜잭션 없이 실행되며, PG 호출 후 상태 업데이트만 트랜잭션으로 처리합니다.
     *
     * 0원 결제는 PgClientResultDecorator에서 NotRequired를 반환합니다.
     * 0원 결제가 아닌 경우 cardInfo가 필수입니다.
     *
     * @param command 결제 요청 커맨드
     * @param currentTime 현재 시각
     * @return PgPaymentResult - 결제 결과
     */
    fun requestPgPayment(
        command: PaymentCommand.RequestPgPayment,
        currentTime: Instant = Instant.now(),
    ): PgPaymentResult {
        val payment = paymentRepository.findById(command.paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")

        val pgResult = pgClient.requestPayment(
            PgPaymentRequest(
                paymentId = payment.id,
                amount = payment.paidAmount,
                cardInfo = command.cardInfo,
            ),
        )

        val result = payment.initiate(pgResult, currentTime)
        paymentRepository.save(payment)

        publishPgPaymentResultEvent(result, payment)

        return result
    }

    /**
     * PG 결제를 요청하고 결제 상태를 업데이트합니다.
     * 이 메서드는 비동기 이벤트 리스너에서 사용됩니다. (cardInfo는 Payment에 저장되어 있음)
     *
     * PG 호출은 트랜잭션 외부에서 수행하여, PG 응답 대기 중 DB 커넥션을 점유하지 않습니다.
     * save + event publish만 트랜잭션 내에서 수행합니다.
     *
     * @param paymentId 결제 ID
     * @param currentTime 현재 시각
     * @return PgPaymentResult - 결제 결과
     */
    @Retryable(
        retryFor = [ObjectOptimisticLockingFailureException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100),
    )
    fun requestPgPayment(
        paymentId: Long,
        currentTime: Instant = Instant.now(),
    ): PgPaymentResult {
        val payment = paymentRepository.findById(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")

        val pgResult = pgClient.requestPayment(
            PgPaymentRequest(
                paymentId = payment.id,
                amount = payment.paidAmount,
                cardInfo = payment.cardInfo,
            ),
        )

        val result = payment.initiate(pgResult, currentTime)

        transactionTemplate.execute {
            paymentRepository.save(payment)
            publishPgPaymentResultEvent(result, payment)
        }

        return result
    }

    private fun publishPgPaymentResultEvent(result: PgPaymentResult, payment: Payment) {
        when (result) {
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

            is PgPaymentResult.InProgress -> {
                /* No event, wait for callback */
            }
        }
    }
}
