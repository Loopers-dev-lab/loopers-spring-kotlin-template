package com.loopers.domain.product.stock

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "stocks",
    indexes = [
        Index(name = "stock_ref_product_idx", columnList = "ref_product_id"),
    ],
)
class StockModel(
    @Column
    val refProductId: Long,

    @Column
    var amount: Long = 0L,
) : BaseEntity() {

    fun occupy(quantity: Long) {
        if (quantity <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "차감 수량은 0보다 커야 합니다.")
        }
        if (this.amount < quantity) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.")
        }
        this.amount -= quantity
    }

    companion object {
        fun create(refProductId: Long, amount: Long): StockModel =
            StockModel(refProductId, amount).apply {
                require(amount >= 0) { "재고 수량은 0 이상이어야 합니다." }
            }
    }
}
