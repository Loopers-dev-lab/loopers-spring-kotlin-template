package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "loopers_product")
class Product(
    @Column(nullable = false, length = 200)
    var name: String,

    @Column(nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,

    @Column(nullable = false)
    var brandId: Long,
) : BaseEntity() {

    init {
        validateName(name)
        validatePrice(price)
    }

    fun update(name: String?, price: BigDecimal?, brandId: Long?) {
        name?.let {
            validateName(it)
            this.name = it
        }
        price?.let {
            validatePrice(it)
            this.price = it
        }
        brandId?.let {
            this.brandId = it
        }
    }

    companion object {
        fun of(
            name: String,
            price: BigDecimal,
            brandId: Long,
        ): Product {
            return Product(
                name = name,
                price = price,
                brandId = brandId,
            )
        }
    }

    private fun validateName(name: String) {
        if (name.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 이름은 비어있을 수 없습니다.")
        }
    }

    private fun validatePrice(price: BigDecimal) {
        if (price < BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품 가격은 0 이상이어야 합니다.")
        }
    }
}
