package com.loopers.domain.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.MemberCoupon
import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberRepository
import com.loopers.domain.coupon.event.CouponUsedEvent
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
import io.mockk.verifyOrder
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
        eventPublisher = mockk(relaxed = true) // 이벤트 발행은 자동으로 허용
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
        val memberCoupon = mockk<MemberCoupon>(relaxed = true)

        val orderItems = listOf(
            OrderItemCommand(productId = 1L, quantity = 2),
            OrderItemCommand(productId = 2L, quantity = 1),
        )

        every {
            productRepository.findAllByIdIn(match { it.size == 2 && it.containsAll(listOf(1L, 2L)) })
        } returns listOf(product1, product2)
        every { memberRepository.findByMemberIdWithLockOrThrow(memberId) } returns member
        every { couponService.getMemberCoupon(any(), any()) } returns memberCoupon
        every { couponService.calculateDiscount(any(), any()) } returns Money.of(5000)
        every { memberCoupon.use() } just Runs
        every { orderRepository.save(any()) } answers { firstArg() }
        // eventPublisher는 relaxed = true로 설정되어 있어서 자동으로 허용됨

        // when
        val result = orderService.createOrderWithCalculation(
            memberId = memberId,
            orderItems = orderItems,
            couponId = 1L,
            usePoint = 3000L,
        )

        // then
        verify(exactly = 1) { couponService.getMemberCoupon(any(), any()) }
        verify(exactly = 1) { couponService.calculateDiscount(any(), any()) }
        verify(exactly = 1) { memberCoupon.use() } // OrderService에서 coupon.use() 직접 호출
        verify(exactly = 1) { member.usePoint(3000L) }
        
        // 주문 생성 결과 검증
        assertThat(result).isNotNull
        assertThat(result.memberId).isEqualTo(memberId)
        assertThat(result.totalAmount.amount).isEqualTo(40000L) // 할인 전 총액
        assertThat(result.discountAmount.amount).isEqualTo(5000L) // 쿠폰 할인 금액
        assertThat(result.finalAmount.amount).isEqualTo(35000L) // 최종 금액 (총액 - 할인)
        // 포인트는 Order에 저장되지 않고 Member에서 차감됨
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
        every {
            productRepository.findAllByIdIn(match { it.size == 1 && it.contains(productId) })
        } returns listOf(product)
        every { memberRepository.findByMemberIdWithLockOrThrow(memberId) } returns member

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

        // 예외 케이스에서 이벤트 미발행 및 포인트 미차감 검증
        verify(exactly = 0) { eventPublisher.publishEvent(any<OrderCreatedEvent>()) }
        verify(exactly = 0) { member.usePoint(any()) }
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

        every {
            productRepository.findAllByIdIn(match { it.size == 1 && it.contains(1L) })
        } returns listOf(product)
        every { memberRepository.findByMemberIdWithLockOrThrow(memberId) } returns member
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
        val now = java.time.Instant.now()
        verify(exactly = 1) { eventPublisher.publishEvent(any<OrderCreatedEvent>()) }
        assertThat(eventSlot.captured.orderId).isEqualTo(result.id)
        assertThat(eventSlot.captured.memberId).isEqualTo(memberId)
        assertThat(eventSlot.captured.orderAmount).isEqualTo(result.totalAmount.amount)
        assertThat(eventSlot.captured.couponId).isNull()
        assertThat(eventSlot.captured.createdAt).isNotNull()
        assertThat(eventSlot.captured.createdAt)
            .isBetween(now.minusSeconds(1), now.plusSeconds(1))
    }

}

