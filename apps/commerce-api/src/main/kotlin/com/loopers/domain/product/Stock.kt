package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.event.DomainEvent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Transient

@Entity
@Table(
    name = "stocks",
    indexes = [
        Index(name = "idx_stocks_product_id", columnList = "product_id", unique = true),
    ],
)
class Stock(
    productId: Long,
    quantity: Int,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false, unique = true)
    var productId: Long = productId
        private set

    @Column(name = "quantity", nullable = false)
    var quantity: Int = quantity
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
        if (quantity < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.")
        }
    }

    companion object {
        fun create(
            productId: Long,
            quantity: Int = 0,
        ): Stock {
            return Stock(productId = productId, quantity = quantity)
        }
    }

    fun decrease(amount: Int) {
        if (amount <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고 감소량은 0보다 커야 합니다.")
        }
        if (this.quantity < amount) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.")
        }
        this.quantity -= amount
        if (this.quantity == 0) {
            getDomainEvents().add(StockDepletedEventV1.from(this))
        }
    }

    fun increase(amount: Int) {
        if (amount <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고 증가량은 0보다 커야 합니다.")
        }
        this.quantity += amount
    }
}
