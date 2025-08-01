package com.loopers.domain.product.dto.command

import com.loopers.domain.product.entity.Product
import java.math.BigDecimal

class ProductCommand {
    data class RegisterProduct(
        val brandId: Long,
        val name: String,
        val description: String,
        val price: BigDecimal,
    ) {
        fun toEntity(): Product {
            return Product.create(brandId, name, description, price)
        }
    }
}
