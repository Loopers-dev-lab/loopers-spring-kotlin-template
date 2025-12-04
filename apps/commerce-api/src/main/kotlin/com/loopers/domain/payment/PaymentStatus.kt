package com.loopers.domain.payment

enum class PaymentStatus(val description: String) {
    NOT_STARTED("결제 미시작"),
    EXECUTING("결제 진행 중"),
    FAIL("결제 실패"),
    SUCCESS("결제 완료"),
}
