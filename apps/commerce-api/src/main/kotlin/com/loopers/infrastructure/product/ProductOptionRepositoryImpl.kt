package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductOptionRepository
import com.loopers.domain.product.entity.ProductOption
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductOptionRepositoryImpl(
    private val productOptionJpaRepository: ProductOptionJpaRepository,
) : ProductOptionRepository {
    override fun find(productOptionId: Long): ProductOption? {
        return productOptionJpaRepository.findByIdOrNull(productOptionId)
    }

    override fun findAll(productOptionIds: List<Long>): List<ProductOption> {
        return productOptionJpaRepository.findAllById(productOptionIds)
    }

    override fun findAll(productIds: Long): List<ProductOption> {
        return productOptionJpaRepository.findAllByProductId(productIds)
    }

    override fun save(productOption: ProductOption): ProductOption {
        return productOptionJpaRepository.save(productOption)
    }
}
