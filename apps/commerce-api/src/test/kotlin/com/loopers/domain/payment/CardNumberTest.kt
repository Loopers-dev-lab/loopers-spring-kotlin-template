package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CardNumberTest {

    @DisplayName("카드번호를 마스킹하여 생성할 수 있다")
    @ParameterizedTest
    @CsvSource(
        "1234567890123456, ************3456",
        "1234, ****",
        "12345, *2345",
        "1234-5678-9012-3456, ***************3456"
    )
    fun createCardNumberWithMasking(plainCardNo: String, expectedMasked: String) {
        val cardNumber = CardNumber.from(plainCardNo)

        assertThat(cardNumber.maskedNumber).isEqualTo(expectedMasked)
        assertThat(cardNumber.toString()).isEqualTo(expectedMasked)
    }

    @DisplayName("잘못된 카드번호로 생성하면 예외가 발생한다")
    @ParameterizedTest
    @CsvSource(
        "'', 카드 번호는 필수입니다",
        "'   ', 카드 번호는 필수입니다",
        "123, 카드 번호는 최소 4자리 이상이어야 합니다"
    )
    fun failToCreateWithInvalidCardNumber(plainCardNo: String, expectedMessage: String) {
        val exception = assertThrows<CoreException> {
            CardNumber.from(plainCardNo)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(exception.message).isEqualTo(expectedMessage)
    }
}
