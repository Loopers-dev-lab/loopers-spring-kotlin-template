package com.loopers.domain.event

import com.loopers.event.EventType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 이벤트 처리의 멱등성을 보장하는 서비스
 *
 * 동일한 Kafka 메시지가 여러 번 처리되어도 최종 결과가 한 번만 반영되도록 보장합니다.
 */
@Component
class EventHandleService(private val eventHandleRepository: EventHandleRepository) {

    private val logger = LoggerFactory.getLogger(EventHandleService::class.java)

    fun duplicatedBy(eventId: String): Boolean {
        val eventType = EventType.LIKE_EVENT.name
        return eventHandleRepository.findByMessageKeyAndEventType(eventId, eventType) != null
    }

    fun ensureLikeEvent(eventId: String) {
        val eventHandle =
            EventHandleModel(
                eventId = eventId,
                eventType = EventType.LIKE_EVENT,
            )
        eventHandleRepository.save(eventHandle)
    }
}
