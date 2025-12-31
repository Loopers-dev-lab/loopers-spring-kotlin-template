package com.loopers.domain.ranking

import com.loopers.support.event.DomainEvent
import java.time.Instant

data class RankingWeightChangedEventV1(
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent {

    companion object {
        fun create(): RankingWeightChangedEventV1 {
            return RankingWeightChangedEventV1()
        }
    }
}
