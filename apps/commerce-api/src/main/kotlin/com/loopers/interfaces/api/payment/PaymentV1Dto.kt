package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentCallbackRequest
import com.loopers.application.payment.PaymentDetailInfo
import com.loopers.application.payment.PaymentInfo
import com.loopers.infrastructure.payment.client.TransactionStatusDto
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

object PaymentV1Dto {
    data class CardPaymentRequest(val orderId: Long, val amount: Long, val cardType: String, val cardNo: String)

    data class PaymentResponse(val paymentId: Long, val transactionKey: String, val status: String, val amount: Long) {
        companion object {
            fun from(info: PaymentInfo): PaymentResponse = PaymentResponse(
                paymentId = info.paymentId,
                transactionKey = info.transactionKey,
                status = info.status,
                amount = info.amount,
            )
        }
    }

    data class PaymentCallbackRequest(val transactionKey: String, val status: String, val reason: String?) {
        fun toApplicationRequest(): com.loopers.application.payment.PaymentCallbackRequest {
            // TransactionStatusDto를 안전하게 파싱
            val transactionStatus = try {
                TransactionStatusDto.valueOf(status)
            } catch (e: IllegalArgumentException) {
                throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "유효하지 않은 결제 상태입니다: $status. 허용된 값: ${TransactionStatusDto.entries.joinToString()}",
                )
            }

            return PaymentCallbackRequest(
                transactionKey = transactionKey,
                status = transactionStatus,
                reason = reason,
            )
        }
    }

    data class PaymentDetailResponse(
        val paymentId: Long,
        val userId: Long,
        val orderId: Long,
        val transactionKey: String?,
        val paymentMethod: String,
        val amount: Long,
        val status: String,
        val failureReason: String?,
        val cardType: String?,
        val cardNo: String?,
    ) {
        companion object {
            fun from(info: PaymentDetailInfo): PaymentDetailResponse = PaymentDetailResponse(
                paymentId = info.paymentId,
                userId = info.userId,
                orderId = info.orderId,
                transactionKey = info.transactionKey,
                paymentMethod = info.paymentMethod,
                amount = info.amount,
                status = info.status,
                failureReason = info.failureReason,
                cardType = info.cardType,
                cardNo = info.cardNo,
            )
        }
    }
}
