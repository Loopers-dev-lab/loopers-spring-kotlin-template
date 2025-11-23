package com.loopers.infrastructure.product.signal

import com.loopers.domain.product.signal.ProductTotalSignalModel
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductTotalSignalJpaRepository : JpaRepository<ProductTotalSignalModel, Long> {
    fun findByRefProductId(refProductId: Long): ProductTotalSignalModel?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductTotalSignalModel p WHERE p.refProductId = :refProductId")
    fun findByRefProductIdWithPessimisticLock(@Param("refProductId") refProductId: Long): ProductTotalSignalModel?
}
