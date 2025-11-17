package com.loopers.infrastructure.product.signal

import com.loopers.domain.product.signal.ProductTotalSignalModel
import com.loopers.domain.product.signal.ProductTotalSignalRepository
import org.springframework.stereotype.Component

@Component
class ProductTotalSignalRepositoryImpl(private val productTotalSignalJpaRepository: ProductTotalSignalJpaRepository) :
    ProductTotalSignalRepository {

    override fun findByProductId(productId: Long): ProductTotalSignalModel? =
        productTotalSignalJpaRepository.findByRefProductId(productId)
}
