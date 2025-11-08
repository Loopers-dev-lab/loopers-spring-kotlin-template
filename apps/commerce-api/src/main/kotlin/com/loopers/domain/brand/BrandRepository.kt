package com.loopers.domain.brand

interface BrandRepository {
    fun findById(id: Long): Brand?
    fun save(brand: Brand): Brand
    fun existsById(id: Long): Boolean
}
