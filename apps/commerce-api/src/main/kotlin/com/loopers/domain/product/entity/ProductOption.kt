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
    color: String,
    size: String,
    displayName: ProductOptionDisplayName,
    additionalPrice: ProductOptionAdditionalPrice,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    @Column(name = "sku_id", nullable = false)
    var skuId: Long = skuId
        protected set

    @Column(name = "color", nullable = false)
    var color: String = color
        protected set

    @Column(name = "size", nullable = false)
    var size: String = size
        protected set

    @Column(name = "display_name", nullable = false)
    var displayName: ProductOptionDisplayName = displayName
        protected set

    @Column(name = "additional_price", nullable = false)
    var additionalPrice: ProductOptionAdditionalPrice = additionalPrice
        protected set

    companion object {
        fun create(
            productId: Long,
            skuId: Long,
            color: String,
            size: String,
            displayName: String,
            additionalPrice: BigDecimal,
        ): ProductOption {
            return ProductOption(
                productId,
                skuId,
                color,
                size,
                ProductOptionDisplayName(displayName),
                ProductOptionAdditionalPrice(additionalPrice),
            )
        }
    }
}
