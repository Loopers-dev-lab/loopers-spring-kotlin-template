package com.loopers.infrastructure.event

import com.loopers.domain.event.EventProcessingTimestamp
import com.loopers.domain.event.EventProcessingTimestampRepository
import org.springframework.stereotype.Repository

@Repository
class EventProcessingTimestampRepositoryImpl(
    private val eventProcessingTimestampJpaRepository: EventProcessingTimestampJpaRepository,
) : EventProcessingTimestampRepository {

    override fun findByConsumerGroupAndAggregateId(
        consumerGroup: String,
        aggregateId: String,
    ): EventProcessingTimestamp? {
        return eventProcessingTimestampJpaRepository.findByConsumerGroupAndAggregateId(consumerGroup, aggregateId)
    }

    override fun save(eventProcessingTimestamp: EventProcessingTimestamp): EventProcessingTimestamp {
        return eventProcessingTimestampJpaRepository.save(eventProcessingTimestamp)
    }
}
