package com.loopers.domain.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberRepository
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Quantity
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher

class OrderServiceTest {

    private lateinit var orderService: OrderService
    private lateinit var orderRepository: OrderRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var memberRepository: MemberRepository
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var couponService: CouponService

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        productRepository = mockk()
        memberRepository = mockk()
        eventPublisher = mockk(relaxed = true)
        couponService = mockk()
        orderService = OrderService(
            orderRepository,
            productRepository,
            memberRepository,
            eventPublisher,
            couponService,
        )
    }

    @DisplayName("쿠폰 할인과 포인트를 사용하여 주문을 생성하고 이벤트를 발행한다")
    @Test
    fun createOrderWithCouponAndPoint() {
        // given
        val memberId = "testuser01"
        val product1 = mockk<Product>(relaxed = true) {
            every { id } returns 1L
            every { name } returns "상품1"
            every { price } returns Money.of(10000L)
            every { validateStock(any()) } just Runs
        }
        val product2 = mockk<Product>(relaxed = true) {
            every { id } returns 2L
            every { name } returns "상품2"
            every { price } returns Money.of(20000L)
            every { validateStock(any()) } just Runs
        }

        val member = mockk<Member>(relaxed = true) {
            every { point.amount } returns 50000L
            every { usePoint(any()) } just Runs
        }

        val orderItems = listOf(
            OrderItemCommand(productId = 1L, quantity = 2),
            OrderItemCommand(productId = 2L, quantity = 1),
        )

        every { productRepository.findAllByIdIn(any()) } returns listOf(product1, product2)
        every { memberRepository.findByMemberIdWithLockOrThrow(memberId) } returns member
        every { couponService.applyAndUseCouponForOrder(any(), any(), any(), any()) } returns Money.of(5000)
        every { orderRepository.save(any()) } answers { firstArg() }

        // 이벤트 캡처 추가
        val eventSlot = slot<OrderCreatedEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        // when
        val result = orderService.createOrderWithCalculation(
            memberId = memberId,
            orderItems = orderItems,
            couponId = 1L,
            usePoint = 3000L,
        )

        // then
        verify(exactly = 1) { couponService.applyAndUseCouponForOrder(any(), any(), any(), any()) }
        verify(exactly = 1) { member.usePoint(3000L) }

        // 이벤트 발행 검증 추가
        verify(exactly = 1) { eventPublisher.publishEvent(any<OrderCreatedEvent>()) }
        assertThat(eventSlot.captured.orderId).isEqualTo(result.id)
        assertThat(eventSlot.captured.memberId).isEqualTo(memberId)
        assertThat(eventSlot.captured.orderAmount).isEqualTo(result.totalAmount.amount)
        assertThat(eventSlot.captured.couponId).isEqualTo(1L)
        assertThat(eventSlot.captured.createdAt).isNotNull()
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

        // 주문 생성 시에는 락 없이 조회 (가격 확인용)
        every { productRepository.findAllByIdIn(listOf(productId)) } returns listOf(product)
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

        // when
        orderService.completeOrderWithPayment(orderId)

        // then
        verify(exactly = 1) { product.decreaseStock(Quantity.of(quantity)) }
        verify(exactly = 1) { order.complete() }
    }

    @DisplayName("쿠폰 없이 주문을 생성하고 이벤트를 발행한다")
    @Test
    fun createOrderWithoutCouponAndPublishEvent() {
        // given
        val memberId = "testuser01"
        val product = mockk<Product>(relaxed = true) {
            every { id } returns 1L
            every { name } returns "상품1"
            every { price } returns Money.of(10000L)
            every { validateStock(any()) } just Runs
        }
        val member = mockk<Member>(relaxed = true) {
            every { point.amount } returns 50000L
        }
        val orderItems = listOf(OrderItemCommand(productId = 1L, quantity = 1))

        every { productRepository.findAllByIdIn(any()) } returns listOf(product)
        every { memberRepository.findByMemberIdWithLockOrThrow(memberId) } returns member
        every { couponService.applyAndUseCouponForOrder(any(), any(), any(), any()) } returns Money.zero()
        every { orderRepository.save(any()) } answers { firstArg() }

        val eventSlot = slot<OrderCreatedEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        // when
        val result = orderService.createOrderWithCalculation(
            memberId = memberId,
            orderItems = orderItems,
            couponId = null,
            usePoint = 0L,
        )

        // then
        verify(exactly = 1) { eventPublisher.publishEvent(any<OrderCreatedEvent>()) }
        assertThat(eventSlot.captured.orderId).isEqualTo(result.id)
        assertThat(eventSlot.captured.memberId).isEqualTo(memberId)
        assertThat(eventSlot.captured.couponId).isNull()
    }

}

