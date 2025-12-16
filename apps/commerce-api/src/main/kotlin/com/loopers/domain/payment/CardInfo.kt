package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

@Embeddable
data class CardInfo(
    @Enumerated(EnumType.STRING)
    @Column(name = "card_type")
    val cardType: CardType,
    @Column(name = "card_no", length = 19)
    val cardNo: String,
) {
    init {
        if (!CARD_NO_PATTERN.matches(cardNo)) {
            throw CoreException(ErrorType.BAD_REQUEST, "카드 번호 형식이 올바르지 않습니다")
        }
    }

    companion object {
        private val CARD_NO_PATTERN = Regex("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$")
    }
}
