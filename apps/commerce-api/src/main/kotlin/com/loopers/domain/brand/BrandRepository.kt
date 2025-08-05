package com.loopers.domain.brand

import com.loopers.domain.brand.entity.Brand

interface BrandRepository {
    fun find(id: Long): Brand?

    fun findAll(ids: List<Long>): List<Brand>

    fun save(brand: Brand): Brand
}
