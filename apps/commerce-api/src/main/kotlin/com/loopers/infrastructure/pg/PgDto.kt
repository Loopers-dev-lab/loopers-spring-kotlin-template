package com.loopers.infrastructure.pg

import com.loopers.domain.payment.type.CardType
import com.loopers.domain.payment.type.TransactionStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

object PgDto {
    data class PaymentRequest(
        val orderId: String,
        val cardType: CardType,
        val cardNo: String?,
        val amount: Long,
        val callbackUrl: String,
    ) {
        companion object {
            private val REGEX_CARD_NO = Regex("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$")
            private const val PREFIX_CALLBACK_URL = "http://localhost:8080"
        }

        fun validate() {
            if (orderId.isBlank() || orderId.length < 6) {
                throw CoreException(ErrorType.BAD_REQUEST, "주문 ID는 6자리 이상 문자열이어야 합니다.")
            }
            if (!REGEX_CARD_NO.matches(cardNo.orEmpty())) {
                throw CoreException(ErrorType.BAD_REQUEST, "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.")
            }
            if (amount <= 0) {
                throw CoreException(ErrorType.BAD_REQUEST, "결제금액은 양의 정수여야 합니다.")
            }
            if (!callbackUrl.startsWith(PREFIX_CALLBACK_URL)) {
                throw CoreException(ErrorType.BAD_REQUEST, "콜백 URL 은 $PREFIX_CALLBACK_URL 로 시작해야 합니다.")
            }
        }

        init {
            validate()
        }
    }

    data class TransactionDetailResponse(
        val transactionKey: String,
        val orderId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val status: TransactionStatus,
        val reason: String?,
    )

    data class TransactionResponse(
        val transactionKey: String,
        val status: TransactionStatus,
        val reason: String?,
    )

    data class OrderResponse(
        val orderId: String,
        val transactions: List<TransactionResponse>,
    )
}
