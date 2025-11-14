package com.loopers.domain.product

interface BrandRepository {
    fun findById(id: Long): Brand?
    fun findAllByIds(ids: List<Long>): List<Brand>
    fun save(brand: Brand): Brand
}
