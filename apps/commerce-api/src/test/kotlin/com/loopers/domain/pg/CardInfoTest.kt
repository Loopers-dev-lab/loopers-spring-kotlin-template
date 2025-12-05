package com.loopers.domain.pg

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CardInfoTest {

    @Test
    fun `유효한 카드 번호 형식으로 CardInfo 생성 성공`() {
        // given
        val cardNo = "1234-5678-9012-3456"

        // when
        val cardInfo = CardInfo(CardType.SAMSUNG, cardNo)

        // then
        assertThat(cardInfo.cardType).isEqualTo(CardType.SAMSUNG)
        assertThat(cardInfo.cardNo).isEqualTo(cardNo)
    }

    @Test
    fun `하이픈 없는 카드 번호는 예외 발생`() {
        // given
        val invalidCardNo = "1234567890123456"

        // when & then
        assertThatThrownBy { CardInfo(CardType.KB, invalidCardNo) }
            .isInstanceOf(CoreException::class.java)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
    }

    @Test
    fun `자릿수가 부족한 카드 번호는 예외 발생`() {
        // given
        val invalidCardNo = "1234-5678-9012-345"

        // when & then
        assertThatThrownBy { CardInfo(CardType.HYUNDAI, invalidCardNo) }
            .isInstanceOf(CoreException::class.java)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
    }

    @Test
    fun `문자가 포함된 카드 번호는 예외 발생`() {
        // given
        val invalidCardNo = "1234-5678-901a-3456"

        // when & then
        assertThatThrownBy { CardInfo(CardType.SAMSUNG, invalidCardNo) }
            .isInstanceOf(CoreException::class.java)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
    }
}
