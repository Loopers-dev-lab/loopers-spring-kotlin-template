package com.loopers.domain.product.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.vo.ProductOptionAdditionalPrice
import com.loopers.domain.product.vo.ProductOptionDisplayName
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "product_option")
class ProductOption protected constructor(
    productId: Long,
    skuId: Long,
    type: ProductOptionType,
    displayName: ProductOptionDisplayName,
    additionalPrice: ProductOptionAdditionalPrice,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    @Column(name = "sku_id", nullable = false)
    var skuId: Long = skuId
        protected set

    @Column(name = "type", nullable = false)
    var type: ProductOptionType = type
        protected set

    @Column(name = "display_name", nullable = false)
    var displayName: ProductOptionDisplayName = displayName
        protected set

    @Column(name = "additional_price", nullable = false)
    var additionalPrice: ProductOptionAdditionalPrice = additionalPrice
        protected set

    enum class ProductOptionType {
        COLOR,
        SIZE,
    }

    companion object {
        fun create(
            productId: Long,
            skuId: Long,
            type: ProductOptionType,
            displayName: String,
            additionalPrice: BigDecimal,
        ): ProductOption {
            return ProductOption(
                productId,
                skuId,
                type,
                ProductOptionDisplayName(displayName),
                ProductOptionAdditionalPrice(additionalPrice),
            )
        }
    }
}
