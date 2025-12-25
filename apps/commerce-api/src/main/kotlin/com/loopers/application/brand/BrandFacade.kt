package com.loopers.application.brand

import com.loopers.domain.brand.BrandQueryService
import org.springframework.stereotype.Component

@Component
class BrandFacade(private val brandQueryService: BrandQueryService) {
    fun getBrand(brandId: Long): BrandInfo {
        val brand = brandQueryService.getBrand(brandId)
        return BrandInfo.from(brand)
    }
}
