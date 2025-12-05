package com.loopers.application.order

import com.loopers.domain.brand.Brand
import com.loopers.domain.member.BirthDate
import com.loopers.domain.member.Gender
import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberId
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Email
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.interfaces.api.order.OrderV1Dto
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class OrderFacadeIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val memberJpaRepository: MemberJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("포인트 전액 결제로 주문을 생성할 수 있다")
    @Test
    fun createOrder() {
        // Given
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        member.chargePoint(100000L)
        memberJpaRepository.save(member)

        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product = productJpaRepository.save(
            Product("상품1", "설명", Money.of(10000L), Stock.of(100), 1L)
        )

        val request = OrderV1Dto.CreateOrderRequest(
            items = listOf(OrderV1Dto.OrderItemRequest(product.id, 2)),
            usePoint = 20000L, // 전액 포인트 결제
            cardType = null,
            cardNo = null,
            couponId = null
        )

        // When
        val result = orderFacade.createOrder("member1", request)

        // Then
        assertThat(result).isNotNull
        assertThat(result.memberId).isEqualTo("member1")
        assertThat(result.status).isEqualTo(OrderStatus.COMPLETED)
        assertThat(result.totalAmount).isEqualTo(20000L)
        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].productId).isEqualTo(product.id)
        assertThat(result.items[0].quantity).isEqualTo(2)
        assertThat(result.items[0].price).isEqualTo(10000L)
        assertThat(result.items[0].subtotal).isEqualTo(20000L)

        // 재고 감소 확인
        val updatedProduct = productJpaRepository.findById(product.id).get()
        assertThat(updatedProduct.stock.quantity).isEqualTo(98)

        // 포인트 차감 확인
        val updatedMember = memberJpaRepository.findById(member.id).get()
        assertThat(updatedMember.point.amount).isEqualTo(80000L)
    }

    @DisplayName("여러 상품을 포함한 주문을 포인트 전액 결제로 생성할 수 있다")
    @Test
    fun createOrderWithMultipleProducts() {
        // Given
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        member.chargePoint(100000L)
        memberJpaRepository.save(member)

        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product1 = productJpaRepository.save(Product("상품1", "설명1", Money.of(10000L), Stock.of(100), 1L))
        val product2 = productJpaRepository.save(Product("상품2", "설명2", Money.of(20000L), Stock.of(50), 1L))

        val request = OrderV1Dto.CreateOrderRequest(
            items = listOf(
                OrderV1Dto.OrderItemRequest(product1.id, 2),
                OrderV1Dto.OrderItemRequest(product2.id, 1)
            ),
            usePoint = 40000L, // 전액 포인트 결제
            cardType = null,
            cardNo = null,
            couponId = null
        )

        // When
        val result = orderFacade.createOrder("member1", request)

        // Then
        assertThat(result.totalAmount).isEqualTo(40000L) // (10000*2) + (20000*1)
        assertThat(result.items).hasSize(2)

        // 재고 감소 확인
        val updatedProduct1 = productJpaRepository.findById(product1.id!!).get()
        assertThat(updatedProduct1.stock.quantity).isEqualTo(98)

        val updatedProduct2 = productJpaRepository.findById(product2.id!!).get()
        assertThat(updatedProduct2.stock.quantity).isEqualTo(49)

        // 포인트 차감 확인
        val updatedMember = memberJpaRepository.findById(member.id!!).get()
        assertThat(updatedMember.point.amount).isEqualTo(60000L)
    }

    @DisplayName("존재하지 않는 회원으로 주문 생성 시 예외가 발생한다")
    @Test
    fun failToCreateOrderWithNonExistentMember() {
        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product = productJpaRepository.save(
            Product("상품1", "설명", Money.of(10000L), Stock.of(100), 1L)
        )

        val request = OrderV1Dto.CreateOrderRequest(
            items = listOf(OrderV1Dto.OrderItemRequest(product.id, 2)),
            usePoint = 20000L,
            cardType = null,
            cardNo = null,
            couponId = null
        )

        val exception = assertThrows<CoreException> {
            orderFacade.createOrder("nonExistentMember", request)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.MEMBER_NOT_FOUND)
    }

    @DisplayName("존재하지 않는 상품으로 주문 생성 시 예외가 발생한다")
    @Test
    fun failToCreateOrderWithNonExistentProduct() {
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        member.chargePoint(100000L)
        memberJpaRepository.save(member)

        val request = OrderV1Dto.CreateOrderRequest(
            items = listOf(OrderV1Dto.OrderItemRequest(999L, 2)),
            usePoint = 20000L,
            cardType = null,
            cardNo = null,
            couponId = null
        )

        val exception = assertThrows<CoreException> {
            orderFacade.createOrder("member1", request)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.PRODUCT_NOT_FOUND)
    }

    @DisplayName("재고가 부족한 경우 주문 생성 시 예외가 발생한다")
    @Test
    fun failToCreateOrderWithInsufficientStock() {
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        member.chargePoint(100000L)
        memberJpaRepository.save(member)

        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product = productJpaRepository.save(
            Product("상품1", "설명", Money.of(10000L), Stock.of(5), 1L)
        )

        val request = OrderV1Dto.CreateOrderRequest(
            items = listOf(OrderV1Dto.OrderItemRequest(product.id, 10)),
            usePoint = 100000L,
            cardType = null,
            cardNo = null,
            couponId = null
        )

        val exception = assertThrows<CoreException> {
            orderFacade.createOrder("member1", request)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.INSUFFICIENT_STOCK)
    }

    @DisplayName("포인트가 부족한 경우 주문 생성 시 예외가 발생한다")
    @Test
    fun failToCreateOrderWithInsufficientPoint() {
        val member = memberJpaRepository.save(
            Member(MemberId("member1"), Email("test@example.com"), BirthDate.from("1990-05-15"), Gender.MALE)
        )
        member.chargePoint(10000L) // 부족한 포인트
        memberJpaRepository.save(member)

        val brand = brandJpaRepository.save(Brand("브랜드1", "설명"))
        val product = productJpaRepository.save(
            Product("상품1", "설명", Money.of(10000L), Stock.of(100), 1L)
        )

        val request = OrderV1Dto.CreateOrderRequest(
            items = listOf(OrderV1Dto.OrderItemRequest(product.id, 2)), // 총 20000원
            usePoint = 20000L, // 부족한 포인트로 전액 결제 시도
            cardType = null,
            cardNo = null,
            couponId = null
        )

        val exception = assertThrows<CoreException> {
            orderFacade.createOrder("member1", request)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.INSUFFICIENT_POINT)
    }
}
