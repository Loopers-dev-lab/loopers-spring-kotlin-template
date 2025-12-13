package com.loopers.support.event

import java.time.Instant

data class UserActionEvent(
    val userId: String,
    val actionType: ActionType,
    val targetEntityType: EntityType,
    val targetEntityId: Long?, // BROWSE 액션은 null 허용
    val metadata: Map<String, String> = emptyMap(),
    val occurredAt: Instant
)

enum class ActionType {
    VIEW, // 상품 상세 조회
    BROWSE, // 상품 목록 조회
    LIKE, // 상품 좋아요
    UNLIKE, // 상품 좋아요 취소
    ORDER, // 주문
}

enum class EntityType {
    PRODUCT,
    ORDER,
    BRAND
}
