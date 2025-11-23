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
    var price: Money,
    @Column
    var refBrandId: Long,
) : BaseEntity() {

    companion object {
        fun create(name: String, price: Money, refBrandId: Long): ProductModel {
            require(name.isNotBlank()) { "상품명은 필수입니다." }

            return ProductModel(name, price, refBrandId)
        }
    }
}
