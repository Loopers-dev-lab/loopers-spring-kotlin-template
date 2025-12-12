package com.loopers.interfaces.event.order

import com.loopers.domain.like.LikeDataPlatformClient
import com.loopers.domain.order.OrderDataPlatformClient
import com.loopers.domain.payment.PaymentPaidEventV1
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.TimeUnit

/**
 * OrderDataPlatformEventListener 통합 테스트
 *
 * 검증 범위:
 * - PaymentPaidEventV1 -> OrderDataPlatformClient.sendOrderCompleted 호출 (AFTER_COMMIT, 비동기)
 */
@SpringBootTest
@DisplayName("OrderDataPlatformEventListener 통합 테스트")
class OrderDataPlatformEventListenerIntegrationTest @Autowired constructor(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val databaseCleanUp: DatabaseCleanUp,
    private val transactionTemplate: TransactionTemplate,
) {
    @MockkBean
    private lateinit var orderDataPlatformClient: OrderDataPlatformClient

    @MockkBean
    private lateinit var likeDataPlatformClient: LikeDataPlatformClient

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("onPaymentPaid")
    inner class OnPaymentPaid {

        @Test
        @DisplayName("PaymentPaidEventV1 발행 시 OrderDataPlatformClient.sendOrderCompleted가 호출된다")
        fun `PaymentPaidEventV1 triggers sendOrderCompleted call`() {
            // given
            val orderId = 1L
            val paymentId = 100L
            val event = PaymentPaidEventV1(
                paymentId = paymentId,
                orderId = orderId,
            )

            every { orderDataPlatformClient.sendOrderCompleted(orderId) } returns true

            // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 비동기 처리이므로 Awaitility 사용
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                verify(exactly = 1) { orderDataPlatformClient.sendOrderCompleted(orderId) }
            }
        }

        @Test
        @DisplayName("PaymentPaidEventV1 발행 시 sendOrderCompleted가 실패해도 예외가 전파되지 않는다")
        fun `PaymentPaidEventV1 handles exception gracefully`() {
            // given
            val orderId = 2L
            val paymentId = 200L
            val event = PaymentPaidEventV1(
                paymentId = paymentId,
                orderId = orderId,
            )

            every { orderDataPlatformClient.sendOrderCompleted(orderId) } throws RuntimeException("External service error")

            // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 비동기 처리이므로 Awaitility 사용, 예외가 발생해도 호출은 됨
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                verify(exactly = 1) { orderDataPlatformClient.sendOrderCompleted(orderId) }
            }
        }
    }
}
