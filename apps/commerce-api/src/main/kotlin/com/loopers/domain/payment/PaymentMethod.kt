package com.loopers.domain.payment

enum class PaymentMethod {
    POINT, // 포인트 결제
    CARD, // 카드 결제 (PG)
    MIXED, // 혼합 결제
}
