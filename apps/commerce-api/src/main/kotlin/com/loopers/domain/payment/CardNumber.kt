package com.loopers.domain.payment

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 카드 번호 Value Object
 * 보안을 위해 마스킹된 번호만 저장
 */
@Embeddable
data class CardNumber private constructor(
    @Column(name = "card_no_masked", length = 20)
    val maskedNumber: String
) {
    companion object {
        /**
         * 원본 카드 번호를 마스킹하여 CardNumber 생성
         *
         * @param plainCardNo 원본 카드 번호 (예: "1234567890123456")
         * @return 마스킹된 CardNumber (예: "************3456")
         */
        fun from(plainCardNo: String): CardNumber {
            require(plainCardNo.isNotBlank()) {
                "카드 번호는 필수입니다"
            }
            require(plainCardNo.length >= 4) {
                "카드 번호는 최소 4자리 이상이어야 합니다"
            }

            val masked = if (plainCardNo.length <= 4) {
                "****"
            } else {
                "*".repeat(plainCardNo.length - 4) + plainCardNo.takeLast(4)
            }

            return CardNumber(masked)
        }

        /**
         * 이미 마스킹된 번호로 생성 (DB 조회 시 사용)
         */
        fun fromMasked(maskedNumber: String): CardNumber {
            return CardNumber(maskedNumber)
        }
    }

    override fun toString(): String = maskedNumber
}
