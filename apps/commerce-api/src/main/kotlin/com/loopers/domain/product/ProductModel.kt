package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "products")
open class ProductModel(name: String, stock: Int, refBrandId: Long) : BaseEntity() {

    @Column
    var name: String = name
        protected set

    @Column
    var stock: Int = stock
        protected set

    @Column
    var brandId: Long = refBrandId
        protected set

    @Column
    var likeCount: Int = 0
        protected set

    fun decreaseStock(quantity: Int) {
        require(quantity > 0) { "감소 수량은 0보다 커야 합니다." }
        require(this.stock >= quantity) { "재고가 부족합니다." }
        this.stock -= quantity
    }

    companion object {
        fun create(name: String, stock: Int, refBrandId: Long): ProductModel {
            require(name.isNotBlank()) { "상품명은 필수입니다." }
            require(stock >= 0) { "재고는 0 이상이어야 합니다." }

            return ProductModel(name, stock, refBrandId)
        }
    }
}
