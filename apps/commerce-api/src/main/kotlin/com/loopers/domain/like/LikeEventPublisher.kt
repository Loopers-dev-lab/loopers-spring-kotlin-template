package com.loopers.domain.like

interface LikeEventPublisher {
    fun publish(event: LikeEvent)
}
