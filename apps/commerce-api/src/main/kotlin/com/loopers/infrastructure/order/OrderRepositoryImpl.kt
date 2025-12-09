package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun save(order: Order): Order {
        return orderJpaRepository.save(order)
    }

    override fun findById(id: Long): Order? {
        return orderJpaRepository.findById(id).orElse(null)
    }

    override fun findByIdOrThrow(id: Long): Order {
        return findById(id)
            ?: throw CoreException(ErrorType.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다. id: $id")
    }

    override fun findByMemberId(
        memberId: String,
        pageable: Pageable,
    ): Page<Order> {
        return orderJpaRepository.findByMemberId(memberId, pageable)
    }

    override fun findByStatusAndCreatedAtBefore(
        status: OrderStatus,
        time: ZonedDateTime
    ): List<Order> {
        return orderJpaRepository.findByStatusAndCreatedAtBefore(status, time)
    }
}
