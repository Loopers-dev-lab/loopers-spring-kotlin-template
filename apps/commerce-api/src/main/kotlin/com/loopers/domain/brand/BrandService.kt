package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service

@Service
class BrandService(
    private val brandRepository: BrandRepository,
) {
    fun getBrand(brandId: Long): BrandResult {
        val brand = brandRepository.findById(brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다: $brandId")
        return BrandResult.from(brand)
    }

    fun getAllBrand(brandId: List<Long>): List<Brand> = brandRepository.findAll(brandId)
}
