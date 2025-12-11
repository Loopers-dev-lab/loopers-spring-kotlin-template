package com.loopers.domain.like

sealed interface LikeEvent {
    val productId: Long
    val userId: Long
}

data class LikedEvent(override val productId: Long, override val userId: Long) : LikeEvent

data class UnlikedEvent(override val productId: Long, override val userId: Long) : LikeEvent
