package com.loopers.domain.brand

import org.springframework.stereotype.Service

@Service
class BrandService(
    private val brandRepository: BrandRepository,
) {
    fun getBrand(brandId: Long): Brand? {
        return brandRepository.findById(brandId)
    }

    fun getAllBrand(brandId: List<Long>): List<Brand> = brandRepository.findAll(brandId)
}
