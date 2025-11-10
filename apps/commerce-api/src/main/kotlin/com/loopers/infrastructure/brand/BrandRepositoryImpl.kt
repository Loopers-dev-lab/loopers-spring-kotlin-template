package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun findById(id: Long): Brand? = brandJpaRepository.findByIdOrNull(id)
}
