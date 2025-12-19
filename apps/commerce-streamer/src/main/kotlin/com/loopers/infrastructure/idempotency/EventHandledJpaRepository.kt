package com.loopers.infrastructure.idempotency

import com.loopers.support.idempotency.EventHandled
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EventHandledJpaRepository : JpaRepository<EventHandled, Long> {

    fun existsByAggregateTypeAndAggregateIdAndAction(
        aggregateType: String,
        aggregateId: String,
        action: String,
    ): Boolean
}
