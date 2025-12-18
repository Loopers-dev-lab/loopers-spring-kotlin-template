package com.loopers.infrastructure.event

import com.loopers.domain.event.EventHandleModel
import org.springframework.data.jpa.repository.JpaRepository

interface EventHandleJpaRepository : JpaRepository<EventHandleModel, Long> {
    fun findByTopicAndPartitionNumberAndOffsetNumber(
            topic: String,
            partitionNumber: Int,
            offsetNumber: Long,
    ): EventHandleModel?

    fun findByMessageKeyAndEventType(
            messageKey: String,
            eventType: String,
    ): EventHandleModel?

    fun findAllByMessageKeyInAndEventType(
            messageKeys: List<String>,
            eventType: String,
    ): List<EventHandleModel>
}

