package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface OrderJpaRepository : JpaRepository<Order, Long> {
    fun findAllByUserId(userId: Long, pageable: Pageable): Page<Order>

    @Modifying
    @Query("UPDATE Order SET status = :orderStatus where id = :id")
    fun update(id: Long, orderStatus: OrderStatus)
}
