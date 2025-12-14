package com.loopers.domain.event

interface EventProcessingTimestampRepository {
    fun findByConsumerGroupAndAggregateId(consumerGroup: String, aggregateId: String): EventProcessingTimestamp?
    fun save(eventProcessingTimestamp: EventProcessingTimestamp): EventProcessingTimestamp
}
