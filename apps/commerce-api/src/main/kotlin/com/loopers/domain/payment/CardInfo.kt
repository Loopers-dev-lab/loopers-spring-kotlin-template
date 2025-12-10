package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class CardInfo(
    val cardType: CardType,
    val cardNo: String,
) {
    init {
        if (!CARD_NO_PATTERN.matches(cardNo)) {
            throw CoreException(ErrorType.BAD_REQUEST, "카드 번호 형식이 올바르지 않습니다")
        }
    }

    companion object {
        private val CARD_NO_PATTERN = Regex("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$")

        /** 0원 결제 시 사용되는 더미 카드 정보 (실제로 사용되지 않음) */
        val EMPTY = CardInfo(CardType.SAMSUNG, "0000-0000-0000-0000")
    }
}
