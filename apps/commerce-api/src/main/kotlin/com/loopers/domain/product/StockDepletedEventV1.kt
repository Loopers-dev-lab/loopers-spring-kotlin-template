package com.loopers.domain.product

import com.loopers.support.event.DomainEvent
import java.time.Instant

data class StockDepletedEventV1(
    val productId: Long,
    val stockId: Long,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent {
    companion object {
        fun from(stock: Stock): StockDepletedEventV1 {
            return StockDepletedEventV1(
                productId = stock.productId,
                stockId = stock.id,
            )
        }
    }
}
