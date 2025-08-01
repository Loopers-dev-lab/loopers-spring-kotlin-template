package com.loopers.infrastructure.brand

import com.loopers.domain.brand.entity.Brand
import com.loopers.domain.brand.BrandRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun find(id: Long): Brand? {
        return brandJpaRepository.findByIdOrNull(id)
    }

    override fun findAll(ids: List<Long>): List<Brand> {
        return brandJpaRepository.findAllById(ids)
    }

    override fun save(brand: Brand): Brand {
        return brandJpaRepository.save(brand)
    }
}
