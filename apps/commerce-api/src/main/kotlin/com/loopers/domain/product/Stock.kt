package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "stocks")
class Stock(
    @Id
    @Column(name = "product_id")
    val productId: Long,

    quantity: Int,
) {
    @Column(nullable = false)
    var quantity: Int = quantity
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
        protected set

    init {
        if (quantity < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.")
        }
    }

    @PrePersist
    private fun prePersist() {
        val now = ZonedDateTime.now()
        updatedAt = now
    }

    @PreUpdate
    private fun preUpdate() {
        updatedAt = ZonedDateTime.now()
    }

    fun decrease(amount: Int) {
        if (amount <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "감소량은 0보다 커야 합니다.")
        }
        if (this.quantity < amount) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고 부족: 현재 재고 $quantity, 요청 수량 $amount")
        }
        this.quantity -= amount
    }

    fun increase(amount: Int) {
        if (amount <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "증가량은 0보다 커야 합니다.")
        }
        this.quantity += amount
    }

    fun isAvailable(amount: Int): Boolean = this.quantity >= amount
}
