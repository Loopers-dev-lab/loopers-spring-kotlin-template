package com.loopers.infrastructure.product

import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class BrandRdbRepository(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    @Transactional(readOnly = true)
    override fun findById(id: Long): Brand? {
        return brandJpaRepository.findById(id).orElse(null)
    }

    @Transactional(readOnly = true)
    override fun findAllByIds(ids: List<Long>): List<Brand> {
        return brandJpaRepository.findAllById(ids)
    }

    @Transactional
    override fun save(brand: Brand): Brand {
        return brandJpaRepository.save(brand)
    }
}
