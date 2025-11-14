package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.common.vo.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "products")
open class ProductModel(
    @Column
    var name: String,
    @Column
    var stock: Int,
    @Column
    var price: Money,
    @Column
    var refBrandId: Long,
) : BaseEntity() {

    fun occupyStock(quantity: Int) {
        require(quantity > 0) { "감소 수량은 0보다 커야 합니다." }
        require(this.stock >= quantity) { "재고가 부족합니다." }
        this.stock -= quantity
    }

    companion object {
        fun create(name: String, stock: Int, price: Money, refBrandId: Long): ProductModel {
            require(name.isNotBlank()) { "상품명은 필수입니다." }
            require(stock >= 0) { "재고는 0 이상이어야 합니다." }

            return ProductModel(name, stock, price, refBrandId)
        }
    }
}
