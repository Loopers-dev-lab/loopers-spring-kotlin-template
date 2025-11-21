package com.loopers.infrastructure.product.stock

import com.loopers.domain.product.stock.StockModel
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface StockJpaRepository : JpaRepository<StockModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockModel s WHERE s.refProductId = :productId")
    fun getStockByRefProductIdWithPessimisticLock(productId: Long): StockModel?

    fun findByRefProductId(refProductId: Long): StockModel?
}
