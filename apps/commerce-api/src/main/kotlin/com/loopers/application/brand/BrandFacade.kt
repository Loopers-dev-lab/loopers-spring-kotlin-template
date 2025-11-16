package com.loopers.application.brand

import com.loopers.domain.brand.BrandResult
import com.loopers.domain.brand.BrandService
import org.springframework.stereotype.Service

@Service
class BrandFacade(
    private val brandService: BrandService,
) {

    fun getBrand(brandId: Long): BrandResult {
        return brandService.getBrand(brandId).let {
            BrandResult.from(it)
        }
    }
}
