package com.loopers.domain.brand

import org.springframework.stereotype.Service

@Service
class BrandService(
    private val brandRepository: BrandRepository,
) {
    fun getBrand(id: Long): Brand? = brandRepository.findById(id)

    fun getAllBrand(ids: List<Long>): List<Brand> = brandRepository.findAll(ids)
}
