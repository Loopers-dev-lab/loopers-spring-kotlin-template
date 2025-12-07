package com.loopers.domain.payment

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.domain.Slice
import java.time.Instant

@Component
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val pgClient: PgClient,
) {
    /**
     * 결제를 생성합니다.
     * paidAmount는 totalAmount - usedPoint - couponDiscount로 자동 계산됩니다.
     * - paidAmount가 0이면 (포인트+쿠폰으로 전액 결제) → PAID
     * - paidAmount가 0보다 크면 (PG 결제 필요) → PENDING
     *
     * @param userId 사용자 ID
     * @param order 주문 엔티티
     * @param usedPoint 사용할 포인트
     * @param issuedCouponId 사용할 쿠폰 ID (nullable)
     * @param couponDiscount 쿠폰 할인 금액
     * @return 생성된 Payment
     */
    @Transactional
    fun createPending(
        userId: Long,
        order: Order,
        usedPoint: Money,
        issuedCouponId: Long? = null,
        couponDiscount: Money = Money.ZERO_KRW,
    ): Payment {
        val payment = Payment.create(
            userId = userId,
            order = order,
            usedPoint = usedPoint,
            issuedCouponId = issuedCouponId,
            couponDiscount = couponDiscount,
        )

        return paymentRepository.save(payment)
    }

    /**
     * PG 트랜잭션 결과로 결제 상태를 확정합니다.
     * Payment.confirmPayment()를 호출하여 상태를 결정합니다.
     *
     * @param paymentId 결제 ID
     * @param transactions PG에서 조회한 트랜잭션 목록
     * @param currentTime 현재 시각 (타임아웃 판단용)
     * @return 상태가 변경된 Payment
     * @throws CoreException 결제를 찾을 수 없는 경우
     */
    @Transactional
    fun confirmPayment(
        paymentId: Long,
        transactions: List<PgTransaction>,
        currentTime: Instant,
    ): Payment {
        val payment = paymentRepository.findById(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")

        payment.confirmPayment(*transactions.toTypedArray(), currentTime = currentTime)

        return paymentRepository.save(payment)
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
     * 외부 결제 키로 결제를 조회합니다.
     * PG 콜백에서 거래를 식별할 때 사용됩니다.
     *
     * @param key PG사 거래 키
     * @return Payment (nullable)
     */
    @Transactional(readOnly = true)
    fun findByExternalPaymentKey(key: String): Payment? {
        return paymentRepository.findByExternalPaymentKey(key)
    }

    /**
     * 결제 ID로 결제를 조회합니다.
     *
     * @param paymentId 결제 ID
     * @return Payment
     * @throws CoreException 결제를 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    fun findById(paymentId: Long): Payment {
        return paymentRepository.findById(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")
    }

    /**
     * 주문 ID로 결제를 조회합니다.
     *
     * @param orderId 주문 ID
     * @return Payment
     * @throws CoreException 결제를 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    fun findByOrderId(orderId: Long): Payment {
        return paymentRepository.findByOrderId(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")
    }

    /**
     * PG 콜백 결과
     */
    sealed class CallbackResult {
        /**
         * 이미 처리된 결제 (멱등성)
         */
        data class AlreadyProcessed(val payment: Payment) : CallbackResult()

        /**
         * 새로 확정된 결제
         */
        data class Confirmed(val payment: Payment) : CallbackResult()
    }

    /**
     * PG 콜백을 처리합니다.
     * orderId로 결제를 조회하고, externalPaymentKey로 PG 트랜잭션을 조회하여 결제를 확정합니다.
     *
     * @param orderId 주문 ID
     * @param externalPaymentKey PG 외부 결제 키
     * @param currentTime 현재 시각 (타임아웃 판단용)
     * @return CallbackResult - 결제 확정 결과
     * @throws CoreException 결제를 찾을 수 없는 경우
     */
    @Transactional
    fun processCallback(
        orderId: Long,
        externalPaymentKey: String,
        currentTime: Instant = Instant.now(),
    ): CallbackResult {
        val payment = paymentRepository.findByOrderId(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")

        val transaction = pgClient.findTransaction(externalPaymentKey)

        val result = payment.confirmPayment(transaction, currentTime = currentTime)

        return when (result) {
            is Payment.ConfirmResult.AlreadyProcessed -> {
                CallbackResult.AlreadyProcessed(payment)
            }

            is Payment.ConfirmResult.Confirmed -> {
                paymentRepository.save(payment)
                CallbackResult.Confirmed(payment)
            }
        }
    }

    /**
     * IN_PROGRESS 상태의 결제를 처리합니다.
     * 스케줄러에서 호출됩니다. paymentId로 PG 트랜잭션을 조회하여 결제를 확정합니다.
     *
     * @param paymentId 결제 ID
     * @param currentTime 현재 시각 (타임아웃 판단용)
     * @return CallbackResult - 결제 확정 결과
     * @throws CoreException 결제를 찾을 수 없는 경우
     */
    @Transactional
    fun processInProgressPayment(
        paymentId: Long,
        currentTime: Instant = Instant.now(),
    ): CallbackResult {
        val payment = paymentRepository.findById(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")

        val transactions = pgClient.findTransactionsByPaymentId(payment.id)

        val result = payment.confirmPayment(*transactions.toTypedArray(), currentTime = currentTime)

        return when (result) {
            is Payment.ConfirmResult.AlreadyProcessed -> {
                CallbackResult.AlreadyProcessed(payment)
            }

            is Payment.ConfirmResult.Confirmed -> {
                paymentRepository.save(payment)
                CallbackResult.Confirmed(payment)
            }
        }
    }

    /**
     * PG 결제를 요청하고 결제 상태를 업데이트합니다.
     * 이 메서드는 트랜잭션 없이 실행되며, PG 호출 후 상태 업데이트만 트랜잭션으로 처리합니다.
     *
     * @param paymentId 결제 ID
     * @param cardInfo 카드 정보
     * @return 상태가 업데이트된 Payment
     */
    fun requestPgPayment(
        paymentId: Long,
        cardInfo: CardInfo,
        currentTime: Instant = Instant.now(),
    ): Payment {
        val payment = paymentRepository.findById(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다")

        val pgResult = pgClient.requestPayment(
            PgPaymentRequest(
                paymentId = payment.id,
                amount = payment.paidAmount,
                cardInfo = cardInfo,
            ),
        )

        payment.initiate(pgResult, currentTime)
        return paymentRepository.save(payment)
    }
}
