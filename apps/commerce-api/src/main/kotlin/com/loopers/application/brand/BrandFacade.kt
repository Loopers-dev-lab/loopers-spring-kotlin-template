package com.loopers.application.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class BrandFacade(
    private val brandService: BrandService,
) {
    fun getBrand(brandId: Long): Brand {
        return brandService.getBrand(brandId)
    }

    fun getBrands(pageable: Pageable): Page<Brand> {
        return brandService.getBrands(pageable)
    }

}
