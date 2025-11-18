package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun save(brand: Brand): Brand {
        return brandJpaRepository.save(brand)
    }

    override fun findById(id: Long): Brand? {
        return brandJpaRepository.findById(id).orElse(null)
    }

    override fun findByIdOrThrow(id: Long): Brand {
        return findById(id)
            ?: throw CoreException(ErrorType.BRAND_NOT_FOUND, "")
    }

    override fun findAll(pageable: Pageable): Page<Brand> {
        return brandJpaRepository.findAll(pageable)
    }


}
