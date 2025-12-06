package com.loopers.interfaces.scheduler

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgService
import com.loopers.domain.product.ProductService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

/**
 * 결제 상태 동기화 스케줄러
 *
 * PG사와의 통신 실패로 인해 PENDING 상태로 남아있는 결제를
 * 주기적으로 PG사에 조회하여 실제 승인된 결제를 복구합니다.
 *
 * 실행 주기: 5분마다
 * 대상: 생성 후 5분이 지난 PENDING 상태의 결제
 */
@Component
class PaymentStatusSyncScheduler(
    private val paymentService: PaymentService,
    private val pgService: PgService,
    private val orderService: OrderService,
    private val productService: ProductService,
    private val couponService: CouponService,
) {

    private val log = LoggerFactory.getLogger(PaymentStatusSyncScheduler::class.java)

    // TODO: 결제 상태가 N분이상 'PENDING'인 경우 실패 처리하는 스케줄러 추가

    @Scheduled(fixedDelay = 300000) // 5분마다
    fun syncPendingPayments() {
        val fiveMinutesAgo = ZonedDateTime.now().minusMinutes(5)
        val pendingPayments = paymentService.findPending(fiveMinutesAgo)

        pendingPayments.forEach { payment ->
            try {
                val orderId = payment.orderId
                val order = orderService.getById(orderId)

                // PG사에 실제 결제 상태 조회
                val statusResponse = pgService.getPaymentByOrderId(
                    userId = payment.userId.toString(),
                    orderId = orderId.toString(),
                )

                if (statusResponse != null) {
                    // 승인된 트랜잭션 찾기
                    val approvedTransaction = statusResponse.transactions
                        .firstOrNull { it.status == PaymentStatus.SUCCESS }

                    // 실패한 트랜잭션 찾기
                    val failedTransaction = statusResponse.transactions
                        .firstOrNull { it.status == PaymentStatus.FAILED }

                    when {
                        approvedTransaction != null -> {
                            // 결제 성공 처리
                            orderService.complete(orderId)
                            paymentService.approvePayment(payment, approvedTransaction.transactionKey)
                            val orderDetails = orderService.getOrderDetail(orderId)
                            productService.deductAllStock(orderDetails)
                            log.info("결제 상태 동기화 성공 (SUCCESS): orderId={}", orderId)
                        }

                        failedTransaction != null -> {
                            // 결제 실패 처리
                            orderService.fail(orderId)
                            paymentService.fail(orderId, failedTransaction.reason)
                            couponService.rollback(order.userId, order.couponId)
                            log.info("결제 상태 동기화 성공 (FAILED): orderId={}", orderId)
                        }

                        else -> {
                            // PG에서 아직 최종 상태가 아닌 경우 다음 스케줄에서 재시도
                            log.debug("결제 상태가 아직 확정되지 않음: orderId={}", orderId)
                        }
                    }
                } else {
                    // PG 조회 실패 시 실패 처리
                    orderService.fail(orderId)
                    paymentService.fail(orderId, "PG 조회 실패로 인한 타임아웃")
                    couponService.rollback(order.userId, order.couponId)
                    log.warn("PG 조회 실패로 타임아웃 처리: orderId={}", orderId)
                }
            } catch (e: Exception) {
                log.error("결제 상태 동기화 중 오류 발생: orderId={}", payment.orderId, e)
            }
        }
    }
}
