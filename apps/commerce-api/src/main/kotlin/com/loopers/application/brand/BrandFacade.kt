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
    fun getBrand(brandId: Long): BrandInfo {
        return brandService.getBrand(brandId)
            .let { BrandInfo.from(it) }
    }

    fun getBrands(pageable: Pageable): Page<BrandInfo> {
        return brandService.getBrands(pageable)
            .map { BrandInfo.from(it) }
    }

}
