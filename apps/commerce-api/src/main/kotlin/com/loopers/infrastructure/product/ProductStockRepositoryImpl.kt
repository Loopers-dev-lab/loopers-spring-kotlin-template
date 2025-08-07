package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.product.entity.ProductStock
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductStockRepositoryImpl(
    private val productStockJpaRepository: ProductStockJpaRepository,
) : ProductStockRepository {
    override fun find(productStockId: Long): ProductStock? {
        return productStockJpaRepository.findByIdOrNull(productStockId)
    }

    override fun findAll(productOptionIds: List<Long>): List<ProductStock> {
        return productStockJpaRepository.findAllById(productOptionIds)
    }

    override fun findAllWithLock(productOptionIds: List<Long>): List<ProductStock> {
        return productStockJpaRepository.findAllWithLock(productOptionIds)
    }

    override fun save(productStock: ProductStock): ProductStock {
        return productStockJpaRepository.save(productStock)
    }
}
