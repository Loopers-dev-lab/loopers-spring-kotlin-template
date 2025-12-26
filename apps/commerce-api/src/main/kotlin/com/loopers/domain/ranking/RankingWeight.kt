package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import com.loopers.support.event.DomainEvent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.math.BigDecimal

@Entity
@Table(name = "ranking_weight")
class RankingWeight(
    viewWeight: BigDecimal,
    likeWeight: BigDecimal,
    orderWeight: BigDecimal,
) : BaseEntity() {

    @Column(name = "view_weight", nullable = false, precision = 3, scale = 2)
    var viewWeight: BigDecimal = viewWeight
        private set

    @Column(name = "like_weight", nullable = false, precision = 3, scale = 2)
    var likeWeight: BigDecimal = likeWeight
        private set

    @Column(name = "order_weight", nullable = false, precision = 3, scale = 2)
    var orderWeight: BigDecimal = orderWeight
        private set

    @Transient
    private var domainEvents: MutableList<DomainEvent>? = null

    private fun getDomainEvents(): MutableList<DomainEvent> {
        if (domainEvents == null) {
            domainEvents = mutableListOf()
        }
        return domainEvents!!
    }

    fun pollEvents(): List<DomainEvent> {
        val events = getDomainEvents().toList()
        getDomainEvents().clear()
        return events
    }

    init {
        validateWeights()
    }

    fun update(
        viewWeight: BigDecimal,
        likeWeight: BigDecimal,
        orderWeight: BigDecimal,
    ): RankingWeight {
        this.viewWeight = viewWeight
        this.likeWeight = likeWeight
        this.orderWeight = orderWeight
        validateWeights()
        getDomainEvents().add(RankingWeightChangedEventV1.create())
        return this
    }

    private fun validateWeights() {
        require(viewWeight >= BigDecimal.ZERO && viewWeight <= BigDecimal.ONE) {
            "viewWeight must be between 0 and 1"
        }
        require(likeWeight >= BigDecimal.ZERO && likeWeight <= BigDecimal.ONE) {
            "likeWeight must be between 0 and 1"
        }
        require(orderWeight >= BigDecimal.ZERO && orderWeight <= BigDecimal.ONE) {
            "orderWeight must be between 0 and 1"
        }
    }

    companion object {
        fun create(
            viewWeight: BigDecimal,
            likeWeight: BigDecimal,
            orderWeight: BigDecimal,
        ): RankingWeight {
            return RankingWeight(
                viewWeight = viewWeight,
                likeWeight = likeWeight,
                orderWeight = orderWeight,
            )
        }

        fun fallback(): RankingWeight {
            return RankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )
        }
    }
}
