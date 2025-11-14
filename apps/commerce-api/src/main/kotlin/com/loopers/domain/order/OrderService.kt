package com.loopers.domain.order

import com.loopers.domain.member.MemberRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.infrastructure.order.ExternalOrderService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val memberRepository: MemberRepository,
    private val externalOrderService: ExternalOrderService,
) {
    @Transactional
    fun createOrder(command: CreateOrderCommand): Order {
        // 1. 상품 조회
        val productIds = command.items.map { it.productId }
        val productMap = productRepository.findAllByIdIn(productIds)
            .associateBy { it.id!! }

        // 2. 회원 조회
        val member = memberRepository.findByMemberIdOrThrow(command.memberId)

        // 3. Order 생성 (상품 검증 및 OrderItem 생성 로직은 Order.create()에 위임)
        val order = Order.create(command.memberId, command.items, productMap)

        // 4. 재고 차감
        order.items.forEach { item ->
            item.product.decreaseStock(item.quantity)
        }

        // 5. 회원 결제 처리 (포인트 검증 및 차감)
        member.pay(order.totalAmount)

        // 6. 주문 저장
        val savedOrder = orderRepository.save(order)

        // 7. 외부 시스템 연동
        externalOrderService.processOrder(savedOrder)
        savedOrder.complete()

        return savedOrder
    }

    @Transactional(readOnly = true)
    fun getOrder(orderId: Long): Order {
        return orderRepository.findByIdOrThrow(orderId)
    }

    @Transactional(readOnly = true)
    fun getOrdersByMemberId(memberId: String, pageable: Pageable): Page<Order> {
        return orderRepository.findByMemberId(memberId, pageable)
    }
}
