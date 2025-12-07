package com.loopers.domain.order

import com.loopers.domain.product.StockService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.PessimisticLockException
import org.springframework.dao.CannotAcquireLockException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val stockService: StockService,
) {
    fun createOrder(userId: Long, orderItems: List<OrderItem>): Order {
        val order = Order(userId = userId, items = orderItems)
        return orderRepository.save(order)
    }

    fun save(order: Order): Order = orderRepository.save(order)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun cancelOrderForPaymentFailure(order: Order) {
        order.cancel()
        orderRepository.save(order)
        // 재고는 메인 트랜잭션 롤백으로 자동 복구됨 (double-restoration 방지)
    }

    @Transactional(timeout = 10)
    @Retryable(
        retryFor = [PessimisticLockException::class, CannotAcquireLockException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100, multiplier = 2.0),
    )
    fun cancelOrder(orderId: Long, userId: Long): Order {
        val order = orderRepository.findByIdWithLock(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다: $orderId")

        if (!order.isOwnedBy(userId)) {
            throw CoreException(ErrorType.FORBIDDEN, "본인의 주문만 취소할 수 있습니다.")
        }

        order.cancel()

        order.items.forEach { item: OrderItem ->
            stockService.increaseStock(item.productId, item.quantity)
        }

        return orderRepository.save(order)
    }
}
