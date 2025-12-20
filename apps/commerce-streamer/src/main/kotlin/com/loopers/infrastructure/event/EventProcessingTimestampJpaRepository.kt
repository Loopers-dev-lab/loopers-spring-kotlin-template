package com.loopers.infrastructure.event

import com.loopers.domain.event.EventProcessingTimestamp
import org.springframework.data.jpa.repository.JpaRepository

interface EventProcessingTimestampJpaRepository : JpaRepository<EventProcessingTimestamp, Long> {
    fun findByConsumerGroupAndAggregateId(consumerGroup: String, aggregateId: String): EventProcessingTimestamp?
}
