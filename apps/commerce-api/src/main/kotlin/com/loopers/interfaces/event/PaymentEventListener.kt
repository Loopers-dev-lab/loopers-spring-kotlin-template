package com.loopers.interfaces.event

import com.loopers.application.order.event.OrderEvent
import com.loopers.application.payment.event.PaymentEvent
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.integration.DataPlatformPublisher
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PgService
import com.loopers.domain.payment.dto.PgCommand
import com.loopers.domain.product.ProductService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate

/**
 * 결제 관련 이벤트를 처리하는 리스너
 *
 * 이벤트 처리 흐름:
 * 1. PaymentCreated (결제 생성)
 *    - AFTER_COMMIT: PG API 요청 (비동기, 외부 I/O)
 *
 * 2. PaymentSucceeded (결제 성공)
 *    - BEFORE_COMMIT: 주문 완료, 재고 차감 (같은 트랜잭션, 원자성 보장)
 *    - 주문 완료 이벤트 발행 → OrderEventListener에서 후속 처리
 *
 * 3. PaymentFailed (결제 실패)
 *    - BEFORE_COMMIT: 주문 실패, 쿠폰 롤백 (같은 트랜잭션, 원자성 보장)
 *    - 주문 실패 이벤트 발행 → OrderEventListener에서 후속 처리
 */
@Component
class PaymentEventListener(
    private val pgService: PgService,
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val productService: ProductService,
    private val couponService: CouponService,
    private val transactionTemplate: TransactionTemplate,
    private val dataPlatformPublisher: DataPlatformPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(PaymentEventListener::class.java)

    /**
     * 결제 생성 이벤트 처리 - PG API 요청
     *
     * AFTER_COMMIT: 주문/결제 트랜잭션 커밋 후 PG API 호출
     * - 외부 API 호출을 트랜잭션 밖에서 처리하여 성능 개선
     * - PG 요청 실패 시 별도 보상 트랜잭션 필요 (Saga 패턴)
     * - transactionKey 업데이트는 새로운 트랜잭션에서 처리
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentCreated(event: PaymentEvent.PaymentCreated) {
        log.info("결제 생성 - PG 요청 시작: paymentId=${event.paymentId}, orderId=${event.orderId}, amount=${event.amount}")

        try {
            // 외부 PG API 호출 (동기 HTTP 요청)
            val transactionKey = pgService.requestPayment(
                command = PgCommand.Request(
                    userId = event.userId,
                    orderId = event.orderId,
                    cardType = event.cardType,
                    cardNo = event.cardNo,
                    amount = event.amount,
                ),
            )

            // PG transactionKey를 새로운 트랜잭션에서 업데이트
            transactionTemplate.execute {
                paymentService.updateTransactionKey(event.paymentId, transactionKey)
            }

            log.info("PG 요청 성공: paymentId=${event.paymentId}, transactionKey=$transactionKey")
        } catch (e: Exception) {
            log.error("PG 요청 실패 - 보상 트랜잭션 필요: paymentId=${event.paymentId}", e)
            // TODO: 결제 실패 처리 또는 재시도 큐 추가
        }
    }

    /**
     * 결제 성공 이벤트 처리 - 주문 완료, 재고 차감
     *
     * BEFORE_COMMIT: 결제 승인 트랜잭션 내에서 주문 완료 및 재고 차감 처리
     * - 주문 상태 변경, 재고 차감이 하나의 트랜잭션으로 원자성 보장
     * - 재고 차감 실패 시 결제도 함께 롤백됨
     * - 주문 완료 이벤트 발행 → OrderEventListener에서 데이터 플랫폼 전송
     *
     * ⚠️ 주의: BEFORE_COMMIT에서는 외부 I/O를 수행하지 않음
     * - 데이터 플랫폼 전송은 OrderEventListener.handleOrderCompleted에서 비동기 처리
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handlePaymentSucceeded(event: PaymentEvent.PaymentSucceeded) {
        log.info("결제 성공 - 주문 완료 처리 시작: orderId=${event.orderId}, userId=${event.userId}, totalAmount=${event.totalAmount}")

        // 1. 주문 상태를 '완료'로 변경
        orderService.complete(event.orderId)
        log.debug("주문 상태 변경 완료: orderId=${event.orderId}")

        // 2. 재고 차감 (동시성 제어: 낙관적 락 또는 비관적 락)
        val orderDetails = orderService.getOrderDetail(event.orderId)
        productService.deductAllStock(orderDetails)
        log.info("재고 차감 완료: orderId=${event.orderId}, items=${orderDetails.size}개")

        // 3. 주문 완료 이벤트 발행
        // → OrderEventListener.handleOrderCompleted에서 데이터 플랫폼 전송 및 사용자 활동 로깅
        applicationEventPublisher.publishEvent(
            OrderEvent.OrderCompleted(
                orderId = event.orderId,
                userId = event.userId,
                totalAmount = event.totalAmount,
                items = orderDetails,
            ),
        )
        log.debug("주문 완료 이벤트 발행 완료: orderId=${event.orderId}")
    }

    /**
     * 결제 실패 이벤트 처리 - 주문 실패, 쿠폰 롤백
     *
     * BEFORE_COMMIT: 결제 실패 트랜잭션 내에서 주문 실패 및 쿠폰 롤백 처리
     * - 주문 상태 변경, 쿠폰 롤백이 하나의 트랜잭션으로 원자성 보장
     * - 주문 실패 이벤트 발행 → OrderEventListener에서 데이터 플랫폼 전송
     *
     * ⚠️ 주의: BEFORE_COMMIT에서는 외부 I/O를 수행하지 않음
     * - 데이터 플랫폼 전송은 OrderEventListener.handleOrderFailed에서 비동기 처리
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handlePaymentFailed(event: PaymentEvent.PaymentFailed) {
        log.info("결제 실패 - 주문 실패 처리 시작: orderId=${event.orderId}, userId=${event.userId}, reason=${event.reason}")

        // 1. 주문 상태를 '실패'로 변경
        orderService.fail(event.orderId)
        log.debug("주문 상태 변경 완료: orderId=${event.orderId}")

        // 2. 쿠폰 롤백 (사용 취소)
        if (event.couponId != null) {
            couponService.rollback(event.userId, event.couponId)
            log.info("쿠폰 롤백 완료: userId=${event.userId}, couponId=${event.couponId}")
        } else {
            log.debug("쿠폰 미사용 주문 - 롤백 스킵: orderId=${event.orderId}")
        }

        // 3. 주문 실패 이벤트 발행
        // → OrderEventListener.handleOrderFailed에서 데이터 플랫폼 전송 및 사용자 활동 로깅
        applicationEventPublisher.publishEvent(
            OrderEvent.OrderFailed(
                orderId = event.orderId,
                userId = event.userId,
                reason = event.reason,
            ),
        )
        log.debug("주문 실패 이벤트 발행 완료: orderId=${event.orderId}")
    }
}
