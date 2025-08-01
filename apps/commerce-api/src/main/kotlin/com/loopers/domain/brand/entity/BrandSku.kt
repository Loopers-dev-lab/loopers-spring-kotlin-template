package com.loopers.domain.brand.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.brand.vo.BrandSkuCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "brand_sku",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_brand_sku_brand_code",
            columnNames = ["brand_id", "code"],
        ),
    ],
)
class BrandSku protected constructor(
    brandId: Long,
    code: BrandSkuCode,
) : BaseEntity() {
    @Column(name = "brand_id", nullable = false)
    var brandId: Long = brandId
        protected set

    @Column(name = "code", nullable = false)
    var code: BrandSkuCode = code
        protected set

    companion object {
        fun create(brandId: Long, code: String): BrandSku {
            return BrandSku(brandId, BrandSkuCode(code))
        }
    }
}
