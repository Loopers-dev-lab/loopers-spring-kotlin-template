package com.loopers.domain.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Quantity
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.order.ExternalOrderService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class OrderServiceTest {

    private lateinit var orderService: OrderService
    private lateinit var orderRepository: OrderRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var memberRepository: MemberRepository
    private lateinit var couponService: CouponService
    private lateinit var externalOrderService: ExternalOrderService

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        productRepository = mockk()
        memberRepository = mockk()
        couponService = mockk()
        externalOrderService = mockk()
        orderService = OrderService(
            orderRepository,
            productRepository,
            memberRepository,
            couponService,
            externalOrderService
        )
    }

    @DisplayName("쿠폰 할인과 포인트를 사용하여 주문을 생성할 수 있다")
    @Test
    fun createOrderWithCouponAndPoint() {
        // given
        val memberId = "testuser01"
        val productId = 1L
        val product = mockk<Product>(relaxed = true) {
            every { id } returns productId
            every { name } returns "상품1"
            every { price } returns Money.of(10000L)
            every { validateStock(any()) } just Runs
        }
        val member = mockk<Member>(relaxed = true) {
            every { point.amount } returns 50000L
        }
        val orderItems = listOf(OrderItemCommand(productId, 1))

        every { productRepository.findAllByIdInWithLock(listOf(productId)) } returns listOf(product)
        every { memberRepository.findByMemberIdWithLockOrThrow(memberId) } returns member
        every { couponService.applyAndUseCouponForOrder(any(), any(), any(), any()) } returns Money.of(1000L)
        every { orderRepository.save(any()) } answers { firstArg() }

        // when
        val order = orderService.createOrderWithCalculation(
            memberId = memberId,
            orderItems = orderItems,
            couponId = 1L,
            usePoint = 3000L
        )

        // then
        assertAll(
            { assertThat(order.memberId).isEqualTo(memberId) },
            { assertThat(order.totalAmount.amount).isEqualTo(10000L) },
            { assertThat(order.discountAmount.amount).isEqualTo(1000L) },
            { assertThat(order.finalAmount.amount).isEqualTo(9000L) },
            { assertThat(order.status).isEqualTo(OrderStatus.PENDING) }
        )
        verify(exactly = 1) { member.usePoint(3000L) }
    }

    @DisplayName("포인트를 초과 사용하면 예외가 발생한다")
    @Test
    fun throwsExceptionWhenPointExceedsFinalAmount() {
        // given
        val memberId = "testuser01"
        val productId = 1L
        val product = mockk<Product>(relaxed = true) {
            every { id } returns productId
            every { name } returns "상품1"
            every { price } returns Money.of(10000L)
            every { validateStock(any()) } just Runs
        }
        val member = mockk<Member>(relaxed = true) {
            every { point.amount } returns 50000L
        }
        val orderItems = listOf(OrderItemCommand(productId, 1))

        every { productRepository.findAllByIdInWithLock(listOf(productId)) } returns listOf(product)
        every { memberRepository.findByMemberIdWithLockOrThrow(memberId) } returns member
        every { couponService.applyAndUseCouponForOrder(any(), any(), any(), any()) } returns Money.zero()

        // when & then
        val exception = assertThrows<CoreException> {
            orderService.createOrderWithCalculation(
                memberId = memberId,
                orderItems = orderItems,
                couponId = null,
                usePoint = 15000L // 10000원 주문에 15000 포인트 사용
            )
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).contains("포인트를 너무 많이 사용했습니다")
    }

    @DisplayName("결제 완료 후 재고가 차감되고 주문이 완료된다")
    @Test
    fun completeOrderWithStockDecrease() {
        // given
        val orderId = 1L
        val productId = 1L
        val quantity = 2

        val product = mockk<Product>(relaxed = true) {
            every { id } returns productId
        }

        val orderItem = OrderItem.of(productId, "상품1", Money.of(10000L), Quantity.of(quantity))
        val order = mockk<Order>(relaxed = true) {
            every { id } returns orderId
            every { items } returns listOf(orderItem)
            every { status } returns OrderStatus.PENDING andThen OrderStatus.COMPLETED
            every { complete() } just Runs
        }

        every { orderRepository.findByIdOrThrow(orderId) } returns order
        every { productRepository.findAllByIdInWithLock(listOf(productId)) } returns listOf(product)
        every { externalOrderService.processOrder(order) } just Runs

        // when
        orderService.completeOrderWithPayment(orderId)

        // then
        verify(exactly = 1) { product.decreaseStock(Quantity.of(quantity)) }
        verify(exactly = 1) { order.complete() }
        verify(exactly = 1) { externalOrderService.processOrder(order) }
    }
}

