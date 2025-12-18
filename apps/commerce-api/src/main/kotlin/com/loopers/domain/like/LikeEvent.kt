package com.loopers.domain.like

sealed interface LikeEvent {
    val productId: Long
    val userId: Long
    val eventId: String
}

data class LikedEvent(override val productId: Long, override val userId: Long, override val eventId: String) : LikeEvent

data class UnlikedEvent(override val productId: Long, override val userId: Long, override val eventId: String) : LikeEvent
