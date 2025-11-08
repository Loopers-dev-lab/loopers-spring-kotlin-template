package com.loopers.application.brand

import com.loopers.domain.brand.BrandRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class BrandFacade(
    private val brandRepository: BrandRepository,
) {
    fun getBrand(brandId: Long): BrandInfo {
        val brand = brandRepository.findById(brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다: $brandId")
        return BrandInfo.from(brand)
    }
}
