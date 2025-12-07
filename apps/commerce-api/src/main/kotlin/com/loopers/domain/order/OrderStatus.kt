package com.loopers.domain.order

enum class OrderStatus(val description: String) {
    PENDING("주문 대기"),
    PAY_PENDING("결제 대기"),
    PAY_FAIL("결제 실패"),
    COMPLETE("주문 성공"),
}
