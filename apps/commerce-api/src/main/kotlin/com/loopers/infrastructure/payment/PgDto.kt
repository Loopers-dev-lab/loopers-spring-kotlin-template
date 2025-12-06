package com.loopers.infrastructure.payment

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.dto.PgInfo
import com.loopers.domain.payment.PaymentStatus

object PgDto {
    data class PgRequest(
        val orderId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val callbackUrl: String? = null,
    )

    data class PgOrderResponse(
        val orderId: String,
        val transactions: List<PgTransactionResponse>,
    ) {
        fun toDomain(): PgInfo.Order {
            return PgInfo.Order(
                orderId = orderId,
                transactions = transactions.map { it.toDomain() },
            )
        }
    }

    data class PgTransactionResponse(
        val transactionKey: String,
        val status: String?,
    ) {
        fun toDomain(): PgInfo.Transaction {
            return PgInfo.Transaction(
                transactionKey = transactionKey,
                orderId = "",
                cardType = CardType.KB,
                cardNo = "",
                amount = 0L,
                status = if (status != null) PaymentStatus.valueOf(status) else PaymentStatus.PENDING,
                reason = null,
            )
        }
    }

    data class PgTransactionDetailResponse(
        val transactionKey: String,
        val orderId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val status: PaymentStatus,
        val reason: String?,
    ) {
        fun toDomain(): PgInfo.Transaction {
            return PgInfo.Transaction(
                transactionKey = transactionKey,
                orderId = orderId,
                cardType = cardType,
                cardNo = cardNo,
                amount = amount,
                status = status,
                reason = reason,
            )
        }
    }
}
