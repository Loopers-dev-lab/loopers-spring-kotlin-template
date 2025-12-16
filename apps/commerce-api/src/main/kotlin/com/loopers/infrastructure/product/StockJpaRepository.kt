package com.loopers.infrastructure.product

import com.loopers.domain.product.Stock
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StockJpaRepository : JpaRepository<Stock, Long> {

    fun findByProductId(productId: Long): Stock?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.productId = :productId")
    fun findByProductIdWithLock(@Param("productId") productId: Long): Stock?

    fun findAllByProductIdIn(productIds: List<Long>): List<Stock>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.productId IN :productIds")
    fun findAllByProductIdsWithLock(@Param("productIds") productIds: List<Long>): List<Stock>
}
