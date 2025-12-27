package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface OrderJpaRepository : JpaRepository<Order, Long> {
    fun findByUserId(userId: Long, pageable: Pageable): Page<Order>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = ["_items"])
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    fun findByIdWithLock(id: Long): Order?
}
