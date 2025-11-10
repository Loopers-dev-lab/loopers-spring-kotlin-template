package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(private val productJpaRepository: ProductJpaRepository) : ProductRepository {

    override fun findById(productId: Long): ProductModel? = productJpaRepository.findByIdOrNull(productId)

    override fun save(product: ProductModel): ProductModel = productJpaRepository.save(product)
}
