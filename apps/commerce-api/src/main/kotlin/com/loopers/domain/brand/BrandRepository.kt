package com.loopers.domain.brand

interface BrandRepository {
    fun findById(brandId: Long): BrandModel?

    fun save(brandModel: BrandModel): BrandModel
}
