package com.loopers.infrastructure.brand

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(private val brandJpaRepository: BrandJpaRepository) : BrandRepository {
    override fun findById(brandId: Long): BrandModel? = brandJpaRepository.findByIdOrNull(brandId)

    override fun save(brandModel: BrandModel): BrandModel = brandJpaRepository.save(brandModel)
}
