package com.loopers.domain.event.handler

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.event.OrderCreatedEvent
import com.loopers.domain.event.PaymentCompletedEvent
import com.loopers.domain.event.PaymentFailedEvent
import com.loopers.domain.event.UserActionEvent
import com.loopers.domain.event.UserActionType
import com.loopers.domain.order.OrderRepository
import com.loopers.infrastructure.dataplatform.client.DataPlatformClient
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderEventHandler(
    private val couponService: CouponService,
    private val orderRepository: OrderRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val dataPlatformClient: DataPlatformClient,
) {
    private val logger = LoggerFactory.getLogger(OrderEventHandler::class.java)

    /**
     * 주문 생성 후 쿠폰 사용 처리
     * 트랜잭션 커밋 후 비동기로 처리
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCreatedForCoupon(event: OrderCreatedEvent) {
        event.couponId?.let { couponId ->
            try {
                logger.info("쿠폰 사용 처리 시작: orderId=${event.orderId}, couponId=$couponId")
                couponService.useUserCoupon(event.userId, couponId)
                logger.info("쿠폰 사용 처리 완료: orderId=${event.orderId}, couponId=$couponId")

                // 유저 행동 로깅
                eventPublisher.publishEvent(
                    UserActionEvent(
                        userId = event.userId,
                        actionType = UserActionType.COUPON_USE,
                        targetType = "COUPON",
                        targetId = couponId,
                        metadata = mapOf("orderId" to event.orderId),
                    ),
                )
            } catch (e: Exception) {
                // 쿠폰 사용 실패는 주문에 영향을 주지 않음 (이미 주문은 생성됨)
                logger.error("쿠폰 사용 실패: orderId=${event.orderId}, couponId=$couponId", e)
            }
        }
    }

    /**
     * 주문 생성 후 데이터 플랫폼 전송
     * 트랜잭션 커밋 후 비동기로 처리
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCreatedForDataPlatform(event: OrderCreatedEvent) {
        try {
            logger.info("데이터 플랫폼 전송 시작: orderId=${event.orderId}")
            dataPlatformClient.sendOrderCreated(event)
            logger.info("데이터 플랫폼 전송 완료: orderId=${event.orderId}")
        } catch (e: Exception) {
            // 데이터 플랫폼 전송 실패는 주문에 영향을 주지 않음
            logger.error("데이터 플랫폼 전송 실패: orderId=${event.orderId}", e)
        }
    }

    /**
     * 결제 완료 후 주문 상태 업데이트
     * 새로운 트랜잭션에서 처리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @EventListener
    fun handlePaymentCompleted(event: PaymentCompletedEvent) {
        try {
            logger.info("주문 상태 업데이트 시작: orderId=${event.orderId}, paymentId=${event.paymentId}")

            val order = orderRepository.findById(event.orderId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다: ${event.orderId}")

            order.confirm()
            orderRepository.save(order)

            logger.info("주문 상태 업데이트 완료: orderId=${event.orderId}, status=${order.status}")

            // 유저 행동 로깅
            eventPublisher.publishEvent(
                UserActionEvent(
                    userId = event.userId,
                    actionType = UserActionType.PAYMENT_COMPLETE,
                    targetType = "ORDER",
                    targetId = event.orderId,
                    metadata = mapOf(
                        "paymentId" to event.paymentId,
                        "amount" to event.amount,
                    ),
                ),
            )
        } catch (e: Exception) {
            logger.error("주문 상태 업데이트 실패: orderId=${event.orderId}", e)
            throw e
        }
    }

    /**
     * 결제 완료 후 데이터 플랫폼 전송
     * 트랜잭션 커밋 후 비동기로 처리
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handlePaymentCompletedForDataPlatform(event: PaymentCompletedEvent) {
        try {
            logger.info("결제 완료 데이터 플랫폼 전송 시작: orderId=${event.orderId}, paymentId=${event.paymentId}")
            dataPlatformClient.sendPaymentCompleted(event)
            logger.info("결제 완료 데이터 플랫폼 전송 완료: orderId=${event.orderId}")
        } catch (e: Exception) {
            // 데이터 플랫폼 전송 실패는 주문에 영향을 주지 않음
            logger.error("결제 완료 데이터 플랫폼 전송 실패: orderId=${event.orderId}", e)
        }
    }

    /**
     * 결제 실패 후 주문 취소 처리
     * 새로운 트랜잭션에서 처리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @EventListener
    fun handlePaymentFailed(event: PaymentFailedEvent) {
        try {
            logger.warn("결제 실패로 인한 주문 처리: orderId=${event.orderId}, reason=${event.reason}")

            val order = orderRepository.findById(event.orderId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다: ${event.orderId}")

            /**
             * 주문 상태를 PENDING으로 유지하여 사용자가 재시도할 수 있도록 함
             * 또는 완전히 취소 처리할 수도 있음 (비즈니스 정책에 따라)
             * 현재 정책: 주문 상태를 변경하지 않고 유지 (사용자 재시도 가능)
             */
            logger.info("결제 실패 처리 완료: orderId=${event.orderId}, status=${order.status}")

            // 유저 행동 로깅
            eventPublisher.publishEvent(
                UserActionEvent(
                    userId = event.userId,
                    actionType = UserActionType.PAYMENT_FAIL,
                    targetType = "ORDER",
                    targetId = event.orderId,
                    metadata = mapOf(
                        "paymentId" to event.paymentId,
                        "reason" to (event.reason ?: "알 수 없음"),
                    ),
                ),
            )
        } catch (e: Exception) {
            logger.error("결제 실패 처리 중 오류: orderId=${event.orderId}", e)
        }
    }
}
