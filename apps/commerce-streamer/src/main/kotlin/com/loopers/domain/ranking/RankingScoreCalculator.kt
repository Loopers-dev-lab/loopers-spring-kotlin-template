package com.loopers.domain.ranking

import com.loopers.domain.event.DomainEvent
import com.loopers.domain.event.like.ProductLikedEvent
import com.loopers.domain.event.like.ProductUnlikedEvent
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.order.event.OrderItemDto
import com.loopers.domain.product.event.ProductViewedEvent
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import kotlin.math.ln

@Component
class RankingScoreCalculator(
    private val weights: RankingWeights
) {
    fun calculateScore(event: DomainEvent): Double {
        return when (event) {
            is ProductViewedEvent -> weights.view
            is ProductLikedEvent -> weights.like
            is ProductUnlikedEvent -> weights.unlike
            is OrderCreatedEvent -> throw IllegalArgumentException(
                "OrderCreatedEvent는 calculateOrderItemScore()를 사용해야 합니다."
            )
            else -> 0.0
        }
    }

    fun calculateOrderItemScore(orderItem: OrderItemDto): Double {
        val orderValue = orderItem.price * orderItem.quantity
        return weights.order * ln(1.0 + orderValue)
    }

    fun calculateBatchScore(events: List<DomainEvent>): Double {
        return events.sumOf {
            try {
                calculateScore(it)
            } catch (e: IllegalArgumentException) {
                0.0
            }
        }
    }
}

@ConfigurationProperties(prefix = "ranking.weights")
data class RankingWeights(
    val view: Double = 0.1,
    val like: Double = 0.2,
    val unlike: Double = -0.2,
    val order: Double = 0.6
)
