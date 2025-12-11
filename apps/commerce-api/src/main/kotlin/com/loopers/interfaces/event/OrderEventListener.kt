package com.loopers.interfaces.event

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.integration.DataPlatformPublisher
import com.loopers.domain.order.OrderEvent
import com.loopers.domain.user.UserActivityEvent
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 관련 이벤트를 처리하는 리스너
 *
 * 이벤트 처리 흐름:
 * 1. OrderCreated (주문 생성)
 *    - BEFORE_COMMIT: 쿠폰 사용 처리 (같은 트랜잭션, 롤백 가능)
 *    - AFTER_COMMIT: 데이터 플랫폼 전송, 사용자 활동 로깅 (비동기)
 *
 * 2. OrderCompleted (주문 완료)
 *    - AFTER_COMMIT: 데이터 플랫폼 전송, 사용자 활동 로깅 (비동기)
 *
 * 3. OrderFailed (주문 실패)
 *    - AFTER_COMMIT: 데이터 플랫폼 전송, 사용자 활동 로깅 (비동기)
 */
@Component
class OrderEventListener(
    private val dataPlatformPublisher: DataPlatformPublisher,
    private val couponService: CouponService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    private val log = LoggerFactory.getLogger(OrderEventListener::class.java)

    /**
     * 주문 생성 이벤트 처리 - 쿠폰 사용
     *
     * BEFORE_COMMIT: 주문 생성 트랜잭션 내에서 쿠폰 사용 처리
     * - 쿠폰 사용 실패 시 주문도 함께 롤백됨 (원자성 보장)
     * - ObjectOptimisticLockingFailureException 발생 시 쿠폰 중복 사용으로 간주
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleOrderCreatedForCoupon(event: OrderEvent.OrderCreated) {
        log.info("주문 생성 - 쿠폰 사용 처리 시작: orderId=${event.orderId}")

        // 할인이 있는 경우에만 쿠폰 사용 처리
        event.couponId?.let { couponId ->
            try {
                couponService.applyCoupon(event.userId, event.couponId)
                log.info("쿠폰 사용 완료: orderId=${event.orderId}, userId=${event.userId}, couponId=${event.couponId}")
            } catch (e: ObjectOptimisticLockingFailureException) {
                // 낙관적 락 실패 = 동시에 다른 트랜잭션에서 이미 쿠폰을 사용함
                log.warn("쿠폰 중복 사용 감지: userId=${event.userId}, couponId=${event.couponId}")
                throw CoreException(ErrorType.COUPON_ALREADY_USED, "이미 사용된 쿠폰입니다")
            }
        }
    }

    /**
     * 주문 생성 후처리 이벤트 처리 - 데이터 플랫폼 전송 및 사용자 활동 로깅
     *
     * AFTER_COMMIT: 주문 생성 트랜잭션 커밋 후 비동기 처리
     * - 외부 시스템 연동(데이터 플랫폼)을 트랜잭션 밖에서 처리하여 성능 개선
     * - 전송 실패해도 주문 생성에는 영향 없음 (Eventual Consistency)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCreatedForUserActivity(event: OrderEvent.OrderCreated) {
        log.info("주문 생성 후처리 시작: orderId=${event.orderId}")

        // 사용자 활동 로깅 이벤트 발행
        applicationEventPublisher.publishEvent(
            UserActivityEvent.UserActivity(
                userId = event.userId,
                activityType = UserActivityEvent.ActivityType.ORDER_PLACED,
                targetId = event.orderId,
                metadata = mapOf(
                    "couponId" to (event.couponId?.toString() ?: "none"),
                ),
            ),
        )
        log.debug("사용자 활동 로깅 이벤트 발행 완료: orderId=${event.orderId}")
    }

    /**
     * 주문 완료 이벤트 처리 - 데이터 플랫폼 전송 및 사용자 활동 로깅
     *
     * AFTER_COMMIT: 주문 완료 트랜잭션 커밋 후 비동기 처리
     * - 결제 성공 후 발행되는 이벤트
     * - 데이터 분석을 위한 정보 전송 및 사용자 활동 추적
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCompleted(event: OrderEvent.OrderCompleted) {
        log.info("주문 완료 후처리 시작: orderId=${event.orderId}, totalAmount=${event.totalAmount}")

        // 데이터 플랫폼으로 주문 완료 정보 전송
        try {
            dataPlatformPublisher.send(event)
            log.info("데이터 플랫폼 전송 완료: orderId=${event.orderId}")
        } catch (e: Exception) {
            log.error("데이터 플랫폼 전송 실패 (재시도 필요): orderId=${event.orderId}", e)
            // TODO: 실패 시 재시도 큐에 추가 또는 Dead Letter Queue로 전송
        }

        // 사용자 활동 로깅 이벤트 발행
        applicationEventPublisher.publishEvent(
            UserActivityEvent.UserActivity(
                userId = event.userId,
                activityType = UserActivityEvent.ActivityType.ORDER_COMPLETED,
                targetId = event.orderId,
                metadata = mapOf(
                    "totalAmount" to event.totalAmount,
                ),
            ),
        )
        log.debug("사용자 활동 로깅 이벤트 발행 완료: orderId=${event.orderId}")
    }

    /**
     * 주문 실패 이벤트 처리 - 데이터 플랫폼 전송 및 사용자 활동 로깅
     *
     * AFTER_COMMIT: 주문 실패 트랜잭션 커밋 후 비동기 처리
     * - 결제 실패 후 발행되는 이벤트
     * - 실패 원인 분석을 위한 정보 수집
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderFailed(event: OrderEvent.OrderFailed) {
        log.info("주문 실패 후처리 시작: orderId=${event.orderId}, reason=${event.reason}")

        // 데이터 플랫폼으로 주문 실패 정보 전송
        try {
            dataPlatformPublisher.send(event)
            log.info("데이터 플랫폼 전송 완료: orderId=${event.orderId}")
        } catch (e: Exception) {
            log.error("데이터 플랫폼 전송 실패 (재시도 필요): orderId=${event.orderId}", e)
            // TODO: 실패 시 재시도 큐에 추가 또는 Dead Letter Queue로 전송
        }

        // 사용자 활동 로깅 이벤트 발행
        applicationEventPublisher.publishEvent(
            UserActivityEvent.UserActivity(
                userId = event.userId,
                activityType = UserActivityEvent.ActivityType.ORDER_FAILED,
                targetId = event.orderId,
                metadata = mapOf("reason" to (event.reason ?: "unknown")),
            ),
        )
        log.debug("사용자 활동 로깅 이벤트 발행 완료: orderId=${event.orderId}")
    }
}
