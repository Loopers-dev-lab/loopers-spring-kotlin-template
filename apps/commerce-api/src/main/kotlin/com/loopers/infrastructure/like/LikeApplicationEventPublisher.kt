package com.loopers.infrastructure.like

import com.loopers.domain.like.LikeEvent
import com.loopers.domain.like.LikeEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class LikeApplicationEventPublisher(private val applicationEventPublisher: ApplicationEventPublisher) : LikeEventPublisher {
    override fun publish(event: LikeEvent) = applicationEventPublisher.publishEvent(event)
}
