package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PaymentCallbackDtoTest {

    @DisplayName("결제 콜백 DTO를 생성할 수 있다")
    @Test
    fun createPaymentCallbackDto() {
        val dto = PaymentCallbackDto(
            transactionKey = "TR-20250104-001",
            status = "SUCCESS",
            reason = null
        )

        assertThat(dto.transactionKey).isEqualTo("TR-20250104-001")
        assertThat(dto.status).isEqualTo("SUCCESS")
    }

    @DisplayName("잘못된 입력으로 생성하면 예외가 발생한다")
    @ParameterizedTest
    @CsvSource(
        "'', SUCCESS, 거래 키는 필수입니다",
        "'   ', SUCCESS, 거래 키는 필수입니다",
        "TR-001, '', 결제 상태는 필수입니다",
        "TR-001, '   ', 결제 상태는 필수입니다"
    )
    fun failToCreateWithInvalidInput(transactionKey: String, status: String, expectedMessage: String) {
        val exception = assertThrows<CoreException> {
            PaymentCallbackDto(transactionKey, status, null)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).isEqualTo(expectedMessage)
    }

    @DisplayName("거래 키가 100자를 초과하면 예외가 발생한다")
    @Test
    fun failToCreateWithLongTransactionKey() {
        val exception = assertThrows<CoreException> {
            PaymentCallbackDto("A".repeat(101), "SUCCESS", null)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).isEqualTo("거래 키는 100자를 초과할 수 없습니다")
    }

    @DisplayName("상태에 따라 isSuccess와 isFailed가 올바르게 동작한다")
    @ParameterizedTest
    @CsvSource(
        "SUCCESS, true, false",
        "FAILED, false, true",
        "PENDING, false, false"
    )
    fun checkStatusMethods(status: String, expectedSuccess: Boolean, expectedFailed: Boolean) {
        val dto = PaymentCallbackDto("TR-001", status, null)

        assertThat(dto.isSuccess()).isEqualTo(expectedSuccess)
        assertThat(dto.isFailed()).isEqualTo(expectedFailed)
    }
}
