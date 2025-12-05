package com.loopers.domain.payment

enum class PaymentStatus {
    PENDING,  // 결제 요청 중
    SUCCESS,  // 결제 성공
    FAILED,   // 결제 실패
    TIMEOUT,  // 타임아웃
    CANCELED  // 취소됨
}
