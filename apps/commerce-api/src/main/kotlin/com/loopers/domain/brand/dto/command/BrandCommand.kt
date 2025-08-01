package com.loopers.domain.brand.dto.command

import com.loopers.domain.brand.entity.Brand

data class BrandCommand(
    val name: String,
    val description: String,
) {
    data class RegisterBrand(
        val name: String,
        val description: String,
    ) {
        fun toEntity(): Brand {
            return Brand.create(name, description)
        }
    }
}
