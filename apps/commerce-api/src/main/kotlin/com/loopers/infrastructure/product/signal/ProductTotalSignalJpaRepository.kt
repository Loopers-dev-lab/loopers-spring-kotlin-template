package com.loopers.infrastructure.product.signal

import com.loopers.domain.product.signal.ProductTotalSignalModel
import org.springframework.data.jpa.repository.JpaRepository

interface ProductTotalSignalJpaRepository : JpaRepository<ProductTotalSignalModel, Long> {
    fun findByRefProductId(refProductId: Long): ProductTotalSignalModel?
}
