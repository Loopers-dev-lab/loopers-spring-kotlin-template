package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.brand.Brand
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "products")
class Product(
    name: String,
    price: Price,
    brand: Brand,
) : BaseEntity() {
    @Column(nullable = false, length = 200)
    var name: String = name
        protected set

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "price_amount", nullable = false, precision = 15, scale = 2)),
        AttributeOverride(name = "currency", column = Column(name = "price_currency", nullable = false, length = 3)),
    )
    var price: Price = price
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false, foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT))
    var brand: Brand = brand
        protected set

    init {
        require(name.isNotBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.")
        }
    }

    fun updatePrice(newPrice: Price) {
        this.price = newPrice
    }

    fun updateName(newName: String) {
        require(newName.isNotBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.")
        }
        this.name = newName
    }
}
