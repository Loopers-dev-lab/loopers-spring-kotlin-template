package com.loopers.infrastructure.event

import com.loopers.domain.event.EventHandleModel
import com.loopers.domain.event.EventHandleRepository
import org.springframework.stereotype.Component

@Component
class EventHandleRepositoryImpl(
        private val eventHandleJpaRepository: EventHandleJpaRepository,
) : EventHandleRepository {

    override fun findByTopicAndPartitionAndOffset(
            topic: String,
            partition: Int,
            offset: Long,
    ): EventHandleModel? {
        return eventHandleJpaRepository.findByTopicAndPartitionNumberAndOffsetNumber(
                topic = topic,
                partitionNumber = partition,
                offsetNumber = offset,
        )
    }

    override fun findByMessageKeyAndEventType(
            messageKey: String,
            eventType: String,
    ): EventHandleModel? {
        return eventHandleJpaRepository.findByMessageKeyAndEventType(messageKey, eventType)
    }

    override fun findAllByMessageKeyInAndEventType(
            messageKeys: List<String>,
            eventType: String,
    ): List<EventHandleModel> {
        return eventHandleJpaRepository.findAllByMessageKeyInAndEventType(messageKeys, eventType)
    }

    override fun save(eventHandle: EventHandleModel): EventHandleModel {
        return eventHandleJpaRepository.saveAndFlush(eventHandle)
    }
}

