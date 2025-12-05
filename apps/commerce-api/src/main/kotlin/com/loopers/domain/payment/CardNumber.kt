package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class CardNumber private constructor(
    @Column(name = "card_no_masked", length = 20)
    val maskedNumber: String
) {
    companion object {
        fun from(plainCardNo: String): CardNumber {
            if (plainCardNo.isBlank()) {
                throw CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다")
            }
            if (plainCardNo.length < 4) {
                throw CoreException(ErrorType.BAD_REQUEST, "카드 번호는 최소 4자리 이상이어야 합니다")
            }

            val masked = if (plainCardNo.length <= 4) {
                "****"
            } else {
                "*".repeat(plainCardNo.length - 4) + plainCardNo.takeLast(4)
            }

            return CardNumber(masked)
        }
    }

    override fun toString(): String = maskedNumber
}
