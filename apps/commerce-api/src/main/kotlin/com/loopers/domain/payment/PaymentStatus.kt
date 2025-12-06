package com.loopers.domain.payment

enum class PaymentStatus {
    PENDING, // 결제 요청 중
    COMPLETED, // 결제 완료
    FAILED, // 결제 실패
    TIMEOUT, // 타임아웃
}
