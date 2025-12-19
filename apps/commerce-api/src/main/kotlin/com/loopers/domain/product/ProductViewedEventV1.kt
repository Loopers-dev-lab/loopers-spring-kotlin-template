package com.loopers.domain.product

import com.loopers.support.event.DomainEvent
import java.time.Instant

data class ProductViewedEventV1(
    val productId: Long,
    val userId: Long?,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent {

    companion object {
        fun create(productId: Long, userId: Long?): ProductViewedEventV1 {
            return ProductViewedEventV1(
                productId = productId,
                userId = userId,
            )
        }
    }
}
