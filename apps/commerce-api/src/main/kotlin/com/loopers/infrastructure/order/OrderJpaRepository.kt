package com.loopers.infrastructure.order

import com.loopers.domain.order.entity.Order
import org.springframework.data.jpa.repository.JpaRepository

interface OrderJpaRepository : JpaRepository<Order, Long>
