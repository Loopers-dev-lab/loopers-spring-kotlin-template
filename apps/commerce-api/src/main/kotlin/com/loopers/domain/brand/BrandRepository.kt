package com.loopers.domain.brand

interface BrandRepository {
    fun findById(id: Long): Brand?
}
