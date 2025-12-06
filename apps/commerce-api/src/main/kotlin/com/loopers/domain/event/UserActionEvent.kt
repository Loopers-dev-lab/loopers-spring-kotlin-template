package com.loopers.domain.event

import java.time.LocalDateTime

/**
 * 유저 행동 이벤트
 * 사용자의 주요 행동을 추적하기 위한 이벤트
 */
data class UserActionEvent(
    val userId: Long,
    val actionType: UserActionType,
    val targetType: String,
    val targetId: Long?,
    val metadata: Map<String, Any>? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

/**
 * 유저 행동 타입
 */
enum class UserActionType {
    PRODUCT_VIEW, // 상품 조회
    PRODUCT_LIKE, // 상품 좋아요
    PRODUCT_UNLIKE, // 상품 좋아요 취소
    ORDER_CREATE, // 주문 생성
    ORDER_CANCEL, // 주문 취소
    PAYMENT_REQUEST, // 결제 요청
    PAYMENT_COMPLETE, // 결제 완료
    PAYMENT_FAIL, // 결제 실패
    COUPON_USE, // 쿠폰 사용
}
