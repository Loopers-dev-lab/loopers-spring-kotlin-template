package com.loopers.support.event

data class UserActionEvent(
    val userId: String,
    val actionType: ActionType,
    val targetEntityType: EntityType,
    val targetEntityId: Long,
    val metadata: Map<String, Any> = emptyMap(),
    val occurredAt: String
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
