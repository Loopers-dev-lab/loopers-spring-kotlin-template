package com.loopers.support.event

import java.time.Instant

data class UserActionEvent(
    val userId: String,
    val actionType: ActionType,
    val targetEntityType: EntityType,
    val targetEntityId: Long,
    val metadata: Map<String, String> = emptyMap(),
    val occurredAt: Instant
)

enum class ActionType {
    VIEW,
    CLICK,
    LIKE,
    ORDER,
    SEARCH
}

enum class EntityType {
    PRODUCT,
    ORDER,
    BRAND
}
