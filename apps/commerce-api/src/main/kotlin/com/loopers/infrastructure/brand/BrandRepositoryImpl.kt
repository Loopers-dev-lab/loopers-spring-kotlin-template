package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun save(brand: Brand): Brand {
        return brandJpaRepository.save(brand)
    }

    override fun findById(id: Long): Brand? {
        return brandJpaRepository.findByIdOrNull(id)
    }

    override fun findAll(): List<Brand> {
        return brandJpaRepository.findAll()
    }
}
