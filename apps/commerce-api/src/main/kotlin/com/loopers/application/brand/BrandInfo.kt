package com.loopers.application.brand

import com.loopers.domain.brand.Brand

data class BrandInfo(
    val id: Long,
    val name: String,
    val description: String?
) {
    companion object {
        fun from(brand: Brand): BrandInfo {
            return BrandInfo(
                id = brand.id,
                name = brand.name,
                description = brand.description
            )
        }
    }
}
