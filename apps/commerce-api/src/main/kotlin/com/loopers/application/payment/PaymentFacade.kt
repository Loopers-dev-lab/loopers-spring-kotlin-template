package com.loopers.application.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentMethod
import com.loopers.domain.payment.PaymentService
import com.loopers.infrastructure.payment.client.TransactionStatusDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentFacade(
    private val paymentService: PaymentService,
) {
    @Transactional
    fun requestCardPayment(request: PaymentRequest): PaymentInfo {
        // 1. 결제 정보 생성
        val payment = paymentService.createPayment(
            userId = request.userId,
            orderId = request.orderId,
            paymentMethod = PaymentMethod.CARD,
            amount = request.amount,
            cardType = request.cardType,
            cardNo = request.cardNo,
        )

        // 2. PG 결제 요청
        val transactionKey = try {
            paymentService.requestPgPayment(payment)
        } catch (e: Exception) {
            // 결제 실패를 별도 트랜잭션에 저장하여 롤백 시에도 실패 기록이 유지되도록 함
            paymentService.savePaymentFailure(payment.id!!, e.message ?: "PG 결제 요청 실패")
            throw e
        }

        // 3. 거래 키 업데이트
        paymentService.updatePaymentWithTransaction(payment.id!!, transactionKey)

        return PaymentInfo.from(payment, transactionKey)
    }

    fun handleCallback(callback: PaymentCallbackRequest) {
        paymentService.handlePaymentCallback(
            transactionKey = callback.transactionKey,
            status = callback.status,
            reason = callback.reason,
        )
    }

    fun getPaymentInfo(transactionKey: String): PaymentDetailInfo {
        val payment = paymentService.getPaymentByTransactionKey(transactionKey)
        return PaymentDetailInfo.from(payment)
    }

    fun getPaymentsByOrderId(orderId: Long): List<PaymentDetailInfo> {
        val payments = paymentService.getPaymentsByOrderId(orderId)
        return payments.map { PaymentDetailInfo.from(it) }
    }
}

data class PaymentRequest(
    val userId: Long,
    val orderId: Long,
    val amount: Long,
    val cardType: String?,
    val cardNo: String?,
)

data class PaymentInfo(
    val paymentId: Long,
    val transactionKey: String,
    val status: String,
    val amount: Long,
) {
    companion object {
        fun from(payment: Payment, transactionKey: String): PaymentInfo {
            return PaymentInfo(
                paymentId = payment.id!!,
                transactionKey = transactionKey,
                status = payment.status.name,
                amount = payment.amount,
            )
        }
    }
}

data class PaymentDetailInfo(
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
        fun from(payment: Payment): PaymentDetailInfo {
            return PaymentDetailInfo(
                paymentId = payment.id!!,
                userId = payment.userId,
                orderId = payment.orderId,
                transactionKey = payment.transactionKey,
                paymentMethod = payment.paymentMethod.name,
                amount = payment.amount,
                status = payment.status.name,
                failureReason = payment.failureReason,
                cardType = payment.cardType,
                cardNo = payment.cardNo,
            )
        }
    }
}

data class PaymentCallbackRequest(
    val transactionKey: String,
    val status: TransactionStatusDto,
    val reason: String?,
)
