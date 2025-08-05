package com.loopers.domain.brand.dto.command

import com.loopers.domain.brand.entity.BrandSku

class BrandSkuCommand() {
    data class RegisterBrandSku(
        val brandId: Long,
        val code: String,
    ) {
        fun toEntity(): BrandSku {
            return BrandSku.create(brandId, code)
        }
    }
}
