package com.loopers.application.payment

import com.loopers.application.payment.event.PaymentEvent
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgService
import com.loopers.domain.payment.dto.PaymentCommand
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFacade(
    private val orderService: OrderService,
    private val paymentService: PaymentService,
    private val pgService: PgService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    private val log = LoggerFactory.getLogger(PaymentFacade::class.java)

    @Transactional
    fun callback(command: PaymentCommand.Callback) {
        val orderId = command.orderId.toLong()
        val transactionKey = command.transactionKey

        val payment = paymentService.getBy(command.transactionKey)

        if (payment.status != PaymentStatus.PENDING) {
            log.warn("이미 처리된 콜백 - transactionKey: $transactionKey orderId: $orderId, 현재 상태: ${payment.status}")
            return
        }

        when (command.status) {
            PaymentStatus.PENDING -> {
                log.info("Payment callback received with PENDING status for orderId: $orderId")
            }

            PaymentStatus.SUCCESS -> {
                paymentService.success(orderId)
                val order = orderService.getById(orderId)

                // 결제 성공 이벤트 발행 (주문 완료, 재고 차감, 사용자 활동 로깅 등)
                applicationEventPublisher.publishEvent(
                    PaymentEvent.PaymentSucceeded(
                        paymentId = payment.id,
                        orderId = orderId,
                        userId = order.userId,
                        totalAmount = order.totalAmount,
                    ),
                )
            }

            PaymentStatus.FAILED -> {
                val order = orderService.getById(orderId)
                paymentService.fail(orderId, command.reason)

                // 결제 실패 이벤트 발행 (주문 실패, 쿠폰 롤백, 사용자 활동 로깅 등)
                applicationEventPublisher.publishEvent(
                    PaymentEvent.PaymentFailed(
                        paymentId = payment.id,
                        orderId = orderId,
                        userId = order.userId,
                        couponId = order.couponId,
                        reason = command.reason,
                    ),
                )
            }
        }
    }

    @Transactional
    fun syncPendingPayment(payment: Payment) {
        val orderId = payment.orderId
        val order = orderService.getById(orderId)

        // PG사에 실제 결제 상태 조회
        val statusResponse = pgService.getPaymentByOrderId(
            userId = payment.userId.toString(),
            orderId = orderId.toString(),
        )

        // PG 조회 실패 시 타임아웃 처리
        if (statusResponse == null) {
            log.warn("PG 조회 실패로 타임아웃 처리: orderId=$orderId")
            paymentService.fail(orderId, "PG 조회 실패로 인한 타임아웃")

            applicationEventPublisher.publishEvent(
                PaymentEvent.PaymentFailed(
                    paymentId = payment.id,
                    orderId = orderId,
                    userId = order.userId,
                    couponId = order.couponId,
                    reason = "PG 조회 실패로 인한 타임아웃",
                ),
            )
            return
        }

        // 승인된 트랜잭션 찾기
        val approvedTransaction = statusResponse.transactions
            .firstOrNull { it.status == PaymentStatus.SUCCESS }

        // 승인된 트랜잭션이 있으면 결제 성공 처리
        if (approvedTransaction != null) {
            log.info("결제 상태 동기화 성공 (SUCCESS): orderId=$orderId")
            paymentService.approvePayment(payment, approvedTransaction.transactionKey)
            applicationEventPublisher.publishEvent(
                PaymentEvent.PaymentSucceeded(
                    paymentId = payment.id,
                    orderId = orderId,
                    userId = order.userId,
                    totalAmount = order.totalAmount,
                ),
            )
            return
        }

        // 실패한 트랜잭션 찾기
        val failedTransaction = statusResponse.transactions
            .firstOrNull { it.status == PaymentStatus.FAILED }

        // 실패한 트랜잭션이 있으면 결제 실패 처리
        if (failedTransaction != null) {
            log.info("결제 상태 동기화 성공 (FAILED): orderId=$orderId")
            paymentService.fail(orderId, failedTransaction.reason)
            applicationEventPublisher.publishEvent(
                PaymentEvent.PaymentFailed(
                    paymentId = payment.id,
                    orderId = orderId,
                    userId = order.userId,
                    couponId = order.couponId,
                    reason = failedTransaction.reason,
                ),
            )
            return
        }

        // PG에서 아직 최종 상태가 아닌 경우 다음 스케줄에서 재시도
        log.debug("결제 상태가 아직 확정되지 않음: orderId=$orderId")
    }
}
