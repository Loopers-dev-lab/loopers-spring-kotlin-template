package com.loopers.domain.event.handler

import com.loopers.domain.event.UserActionEvent
import com.loopers.domain.event.UserActionType
import com.loopers.infrastructure.analytics.client.AnalyticsClient
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 유저 행동 이벤트 핸들러
 * 유저의 행동을 로깅하고 추적
 */
@Component
class UserActionEventHandler(private val analyticsClient: AnalyticsClient) {
    private val logger = LoggerFactory.getLogger(UserActionEventHandler::class.java)

    /**
     * 유저 행동 로깅
     * 비동기로 처리하여 메인 흐름에 영향을 주지 않음
     */
    @Async
    @EventListener
    fun handleUserAction(event: UserActionEvent) {
        try {
            val logMessage = buildLogMessage(event)
            logger.info(logMessage)
            analyticsClient.sendUserAction(event)
        } catch (e: Exception) {
            logger.error("유저 행동 로깅 실패: userId=${event.userId}, action=${event.actionType}", e)
        }
    }

    private fun buildLogMessage(event: UserActionEvent): String {
        val metadata = event.metadata?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: ""
        return when (event.actionType) {
            UserActionType.PRODUCT_VIEW ->
                "[USER_ACTION] 상품 조회 - userId=${event.userId}, productId=${event.targetId}"

            UserActionType.PRODUCT_LIKE ->
                "[USER_ACTION] 상품 좋아요 - userId=${event.userId}, productId=${event.targetId}"

            UserActionType.PRODUCT_UNLIKE ->
                "[USER_ACTION] 상품 좋아요 취소 - userId=${event.userId}, productId=${event.targetId}"

            UserActionType.ORDER_CREATE ->
                "[USER_ACTION] 주문 생성 - userId=${event.userId}, orderId=${event.targetId}, $metadata"

            UserActionType.ORDER_CANCEL ->
                "[USER_ACTION] 주문 취소 - userId=${event.userId}, orderId=${event.targetId}, $metadata"

            UserActionType.PAYMENT_REQUEST ->
                "[USER_ACTION] 결제 요청 - userId=${event.userId}, orderId=${event.targetId}, $metadata"

            UserActionType.PAYMENT_COMPLETE ->
                "[USER_ACTION] 결제 완료 - userId=${event.userId}, orderId=${event.targetId}, $metadata"

            UserActionType.PAYMENT_FAIL ->
                "[USER_ACTION] 결제 실패 - userId=${event.userId}, orderId=${event.targetId}, $metadata"

            UserActionType.COUPON_USE ->
                "[USER_ACTION] 쿠폰 사용 - userId=${event.userId}, couponId=${event.targetId}, $metadata"
        }
    }
}
