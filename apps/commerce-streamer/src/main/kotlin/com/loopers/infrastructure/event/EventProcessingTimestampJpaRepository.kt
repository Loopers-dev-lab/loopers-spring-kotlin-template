package com.loopers.infrastructure.event

import com.loopers.domain.event.EventProcessingTimestamp
import com.loopers.domain.event.EventProcessingTimestampId
import org.springframework.data.jpa.repository.JpaRepository

interface EventProcessingTimestampJpaRepository : JpaRepository<EventProcessingTimestamp, EventProcessingTimestampId> {
    fun findByConsumerGroupAndAggregateId(consumerGroup: String, aggregateId: String): EventProcessingTimestamp?
}
