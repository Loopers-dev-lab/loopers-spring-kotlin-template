package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("PgPaymentRequest 테스트")
class PgPaymentRequestTest {

    @Nested
    @DisplayName("생성")
    inner class `create` {

        @Test
        @DisplayName("양수 금액으로 생성하면 성공한다")
        fun `succeeds with positive amount`() {
            // given
            val paymentId = 1L
            val amount = Money.krw(10000)
            val cardInfo = CardInfo(CardType.SAMSUNG, "1234-5678-9012-3456")

            // when
            val request = PgPaymentRequest(paymentId, amount, cardInfo)

            // then
            assertThat(request.paymentId).isEqualTo(paymentId)
            assertThat(request.amount).isEqualTo(amount)
            assertThat(request.cardInfo).isEqualTo(cardInfo)
        }

        @ParameterizedTest(name = "금액이 {0}원이면 예외가 발생한다")
        @ValueSource(longs = [0, -1, -100, -10000])
        @DisplayName("금액이 0 이하이면 예외가 발생한다")
        fun `throws exception when amount is zero or negative`(amountValue: Long) {
            // given
            val paymentId = 1L
            val amount = Money.krw(amountValue)
            val cardInfo = CardInfo(CardType.KB, "1234-5678-9012-3456")

            // when
            val exception = assertThrows<CoreException> {
                PgPaymentRequest(paymentId, amount, cardInfo)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.customMessage).isEqualTo("PG 결제 요청 금액은 0보다 커야 합니다")
        }
    }
}
