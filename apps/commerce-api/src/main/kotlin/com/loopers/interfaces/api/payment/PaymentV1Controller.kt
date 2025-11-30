package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.PaymentRequest
import com.loopers.interfaces.api.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class PaymentV1Controller(
    private val paymentFacade: PaymentFacade,
) : PaymentV1ApiSpec {
    private val logger = LoggerFactory.getLogger(PaymentV1Controller::class.java)

    override fun requestCardPayment(
        userId: Long,
        request: PaymentV1Dto.CardPaymentRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        logger.info("카드 결제 요청: userId=$userId, orderId=${request.orderId}, amount=${request.amount}")

        val paymentRequest = PaymentRequest(
            userId = userId,
            orderId = request.orderId,
            amount = request.amount,
            cardType = request.cardType,
            cardNo = request.cardNo,
        )

        val paymentInfo = paymentFacade.requestCardPayment(paymentRequest)
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(paymentInfo))
    }

    override fun paymentCallback(
        callback: PaymentV1Dto.PaymentCallbackRequest,
    ): ApiResponse<Unit> {
        logger.info("결제 콜백 수신: transactionKey=${callback.transactionKey}, status=${callback.status}")

        paymentFacade.handleCallback(callback.toApplicationRequest())
        return ApiResponse.success(Unit)
    }

    override fun getPayment(
        userId: Long,
        transactionKey: String,
    ): ApiResponse<PaymentV1Dto.PaymentDetailResponse> {
        val paymentInfo = paymentFacade.getPaymentInfo(transactionKey)
        return ApiResponse.success(PaymentV1Dto.PaymentDetailResponse.from(paymentInfo))
    }

    override fun getPaymentsByOrderId(
        userId: Long,
        orderId: Long,
    ): ApiResponse<List<PaymentV1Dto.PaymentDetailResponse>> {
        val payments = paymentFacade.getPaymentsByOrderId(orderId)
        return ApiResponse.success(payments.map { PaymentV1Dto.PaymentDetailResponse.from(it) })
    }
}
