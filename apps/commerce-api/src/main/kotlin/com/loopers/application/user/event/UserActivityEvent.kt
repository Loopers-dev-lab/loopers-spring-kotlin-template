package com.loopers.application.user.event

import java.time.LocalDateTime

/**
 * 사용자 활동 추적을 위한 이벤트
 * 로깅 및 분석을 위해 사용
 */
object UserActivityEvent {
    enum class ActivityType {
        PRODUCT_LIKE,
        PRODUCT_UNLIKE,
        ORDER_PLACED,
        ORDER_COMPLETED,
        ORDER_FAILED,
        PAYMENT_REQUEST,
        PAYMENT_COMPLETE,
        PAYMENT_FAIL,
        COUPON_USE,
    }

    data class UserActivity(
        val userId: Long,
        val activityType: ActivityType,
        val targetId: Long?,
        val metadata: Map<String, Any> = emptyMap(),
        val timestamp: LocalDateTime = LocalDateTime.now(),
    )
}
