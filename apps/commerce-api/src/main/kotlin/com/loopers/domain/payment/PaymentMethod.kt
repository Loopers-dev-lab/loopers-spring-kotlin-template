package com.loopers.domain.payment

enum class PaymentMethod(
    val description: String
) {
    POINT("포인트 결제"),
    CARD("카드 결제")
    ;
}
