package com.loopers.domain.payment

import com.loopers.domain.payment.dto.command.PaymentCommand
import com.loopers.domain.payment.entity.Payment
import com.loopers.domain.payment.entity.Payment.Method.POINT
import com.loopers.domain.payment.entity.Payment.Status.REQUESTED
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest
class PaymentServiceIntegrationTest @Autowired constructor(
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("결제 요청")
    @Nested
    inner class Request {
        @Test
        fun `결제를 요청하면 저장된다`() {
            // given
            val create = PaymentCommand.Request(1L, POINT, "KB", "1111-2222-3333-4444").toEntity(BigDecimal("1000"))

            // when
            val payment = paymentService.request(create)

            // then
            assertThat(payment.orderId).isEqualTo(create.orderId)
            assertThat(payment.paymentMethod).isEqualTo(create.paymentMethod)
            assertThat(payment.paymentPrice.value).isEqualByComparingTo(create.paymentPrice.value)
            assertThat(payment.status).isEqualTo(REQUESTED)
        }

        @Test
        fun `0보다 작은 금액을 결제 요청하면 예외가 발생한다`() {
            // expect
            assertThrows<CoreException> {
                val create = PaymentCommand.Request(1L, POINT, "KB", "1111-2222-3333-4444").toEntity(BigDecimal("-1"))
                paymentService.request(create)
            }
        }
    }

    @DisplayName("상세 조회")
    @Nested
    inner class Get {
        @Test
        fun `id로 결제를 조회한다`() {
            // given
            val saved = paymentRepository.save(
                Payment.create(1L, POINT, BigDecimal("1"), REQUESTED),
            )

            // when
            val payment = paymentService.get(saved.id)

            // then
            assertThat(payment.id).isEqualTo(saved.id)
            assertThat(payment.paymentMethod).isEqualTo(POINT)
        }

        @Test
        fun `존재하지 않는 결제를 조회하면 예외가 발생한다`() {
            // expect
            assertThrows<CoreException> {
                paymentService.get(-1L)
            }
        }
    }
}
