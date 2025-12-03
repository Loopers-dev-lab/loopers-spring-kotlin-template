package com.loopers.domain.order

enum class OrderStatus {
    PENDING, // 주문 생성, 결제 대기
    PAYMENT_FAILED, // 결제 실패
    COMPLETED, // 주문 완료 (결제 성공)
    CANCELLED, // 취소
}
