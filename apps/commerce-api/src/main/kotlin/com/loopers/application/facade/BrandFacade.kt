package com.loopers.application.facade

import com.loopers.domain.brand.BrandResult
import com.loopers.domain.brand.BrandService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service

@Service
class BrandFacade(
    private val brandService: BrandService,
) {

    fun getBrand(brandId: Long): BrandResult {
        val brand = brandService.getBrand(brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다: $brandId")
        return BrandResult.from(brand)
    }
}
