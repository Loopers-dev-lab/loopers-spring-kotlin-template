package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentPageQuery
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentSortType
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime

@SpringBootTest
@DisplayName("PaymentRdbRepository 통합 테스트")
class PaymentRdbRepositoryTest @Autowired constructor(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("findAllBy with createdBefore")
    inner class FindAllByWithCreatedBefore {

        @Test
        @DisplayName("createdBefore 조건이 있으면 해당 시각 이전에 생성된 결제만 조회한다")
        fun `findAllBy with createdBefore filters payments correctly`() {
            // given
            val payment1 = createPendingPayment()
            val payment2 = createPendingPayment()
            val payment3 = createPendingPayment()

            // threshold 설정: 현재 시각 + 1분 (모든 결제가 포함됨)
            val threshold = ZonedDateTime.now().plusMinutes(1)

            val query = PaymentPageQuery(
                page = 0,
                size = 10,
                sort = PaymentSortType.CREATED_AT_ASC,
                statuses = listOf(PaymentStatus.PENDING),
                createdBefore = threshold,
            )

            // when
            val result = paymentRepository.findAllBy(query)

            // then
            assertThat(result.content).hasSize(3)
            assertThat(result.content.map { it.id }).containsExactlyInAnyOrder(
                payment1.id,
                payment2.id,
                payment3.id,
            )
        }

        @Test
        @DisplayName("createdBefore 조건이 과거 시각이면 해당 시각 이전에 생성된 결제만 조회한다")
        fun `findAllBy with past createdBefore excludes recent payments`() {
            // given
            createPendingPayment()
            createPendingPayment()

            // threshold 설정: 과거 시각 (어떤 결제도 포함되지 않음)
            val threshold = ZonedDateTime.now().minusMinutes(10)

            val query = PaymentPageQuery(
                page = 0,
                size = 10,
                sort = PaymentSortType.CREATED_AT_ASC,
                statuses = listOf(PaymentStatus.PENDING),
                createdBefore = threshold,
            )

            // when
            val result = paymentRepository.findAllBy(query)

            // then
            assertThat(result.content).isEmpty()
        }

        @Test
        @DisplayName("createdBefore 조건이 없으면 모든 매칭 결제를 조회한다")
        fun `findAllBy without createdBefore returns all matching payments`() {
            // given
            val payment1 = createPendingPayment()
            val payment2 = createPendingPayment()
            val payment3 = createPendingPayment()

            val query = PaymentPageQuery(
                page = 0,
                size = 10,
                sort = PaymentSortType.CREATED_AT_ASC,
                statuses = listOf(PaymentStatus.PENDING),
                createdBefore = null,
            )

            // when
            val result = paymentRepository.findAllBy(query)

            // then
            assertThat(result.content).hasSize(3)
            assertThat(result.content.map { it.id }).containsExactlyInAnyOrder(
                payment1.id,
                payment2.id,
                payment3.id,
            )
        }
    }

    // ===========================================
    // Helper Methods
    // ===========================================

    private fun createOrder(
        userId: Long = 1L,
        totalAmount: Money = Money.krw(10000),
    ): Order {
        val order = Order.place(userId)
        order.addOrderItem(
            productId = 1L,
            quantity = 1,
            productName = "Test Product",
            unitPrice = totalAmount,
        )
        return orderRepository.save(order)
    }

    private fun createPendingPayment(
        userId: Long = 1L,
    ): Payment {
        val order = createOrder()
        val payment = Payment.create(
            userId = userId,
            orderId = order.id,
            totalAmount = order.totalAmount,
            usedPoint = Money.ZERO_KRW,
            issuedCouponId = null,
            couponDiscount = Money.ZERO_KRW,
            cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456"),
        )
        return paymentRepository.save(payment)
    }
}
