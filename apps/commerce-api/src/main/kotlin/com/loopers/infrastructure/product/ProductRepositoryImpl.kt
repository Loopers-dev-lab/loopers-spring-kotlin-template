package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun save(product: Product): Product {
        return productJpaRepository.save(product)
    }

    override fun findById(id: Long): Product? {
        return productJpaRepository.findByIdOrNull(id)
    }

    override fun findByIdWithLock(id: Long): Product? {
        return productJpaRepository.findByIdWithLock(id)
    }

    override fun findAll(): List<Product> {
        return productJpaRepository.findAll()
    }

    override fun findByBrandId(brandId: Long): List<Product> {
        return productJpaRepository.findByBrandId(brandId)
    }

    override fun existsById(id: Long): Boolean {
        return productJpaRepository.existsById(id)
    }
}
