package com.loopers.domain.order

enum class OrderStatus(val description: String) {
    PENDING("주문 대기"),
    COMPLETED("주문 완료"),
    FAILED("주문 실패"),
    CANCELLED("주문 취소"),
    ;

    fun isPending() = this == PENDING
    fun isCompleted() = this == COMPLETED
    fun isFailed() = this == FAILED
    fun isCancelled() = this == CANCELLED
}
