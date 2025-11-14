package com.loopers.domain.brand

interface BrandRepository {
    fun save(brand: Brand): Brand
    fun findById(id: Long): Brand?
    fun findByIdIn(ids: List<Long>): List<Brand>
}
