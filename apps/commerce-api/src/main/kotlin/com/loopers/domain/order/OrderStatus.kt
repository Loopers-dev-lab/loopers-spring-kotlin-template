package com.loopers.domain.order

enum class OrderStatus(val description: String) {
    PENDING("주문 대기"),
    COMPLETE("주문 성공"),
}
