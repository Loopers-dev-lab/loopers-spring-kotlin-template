package com.loopers.domain.userAction

enum class ActionType(val description: String) {
    LIKE("좋아요"),
    UNLIKE("좋아요 취소"),
    ORDER("주문"),
    CANCEL_ORDER("주문 취소"),
    VIEW("상품 조회"),
}
