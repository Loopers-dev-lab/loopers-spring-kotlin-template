package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BrandService(
    private val brandRepository: BrandRepository,
) {
    @Transactional(readOnly = true)
    fun getBrand(id: Long): Brand =
        brandRepository.findById(id) ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다: $id")

    @Transactional(readOnly = true)
    fun getAllBrand(ids: List<Long>): List<Brand> = brandRepository.findAll(ids)
}
