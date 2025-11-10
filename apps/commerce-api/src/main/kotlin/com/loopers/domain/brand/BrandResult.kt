package com.loopers.domain.brand

data class BrandResult(
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(brand: Brand): BrandResult = BrandResult(
            id = brand.id,
            name = brand.name,
        )
    }
}
