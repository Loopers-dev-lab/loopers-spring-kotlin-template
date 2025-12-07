package com.loopers.domain.payment

import com.loopers.domain.common.vo.Money
import com.loopers.domain.payment.dto.PaymentDto
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentService(private val paymentClient: PaymentClient, private val paymentRepository: PaymentRepository) {

    companion object {
        private val logger = LoggerFactory.getLogger(PaymentService::class.java)
    }

    @Transactional
    fun pay(request: PaymentDto.Request): PaymentDto.Result {
        val payment = PaymentModel.create(
            refOrderKey = request.orderId,
            amount = Money(request.amount),
            transactionKey = "",
            cardType = request.cardType,
            cardNo = request.cardNo,
        )

        val savedPayment = paymentRepository.save(payment)

        return try {
            val response = paymentClient.requestPayment(request)
            savedPayment.updateTransactionKey(response.data?.transactionKey ?: "")
            savedPayment.updateStatus(PaymentStatus.SUCCESS)

            PaymentDto.Result.Success(paymentRepository.save(savedPayment))
        } catch (e: Exception) {
            logger.error("Payment request failed: ${e.message}")
            savedPayment.updateStatus(PaymentStatus.NOT_STARTED)
            PaymentDto.Result.Failed("결제 요청 실패: ${e.message}")
        }
    }

    @Transactional
    fun updatePaymentStatus(transactionKey: String, status: String): PaymentModel {
        logger.info("Updating payment status: transactionKey=$transactionKey, status=$status")

        val payment = paymentRepository.findByTransactionKey(transactionKey)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[ $transactionKey ] 해당 결제 정보가 존재하지 않습니다.")

        payment.updateStatus(PaymentStatus.valueOf(status))

        return paymentRepository.save(payment)
    }

    fun findFailedPaymentByOrderKey(orderKey: String): PaymentModel? =
        paymentRepository.findFirstByRefOrderKeyAndStatusOrderByCreatedAtDesc(orderKey, PaymentStatus.NOT_STARTED)
}
