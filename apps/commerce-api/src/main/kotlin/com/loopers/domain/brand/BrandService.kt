package com.loopers.domain.brand

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandService(
    private val brandRepository: BrandRepository,
) {

    @Transactional(readOnly = true)
    fun getBrand(brandId: Long): Brand {
        return brandRepository.findByIdOrThrow(brandId)
    }

    @Transactional(readOnly = true)
    fun getBrands(pageable: Pageable): Page<Brand> {
        return brandRepository.findAll(pageable)
    }
}
