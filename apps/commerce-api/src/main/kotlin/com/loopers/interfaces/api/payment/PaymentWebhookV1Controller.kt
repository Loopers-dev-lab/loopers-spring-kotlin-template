package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentCriteria
import com.loopers.application.payment.PaymentFacade
import com.loopers.interfaces.api.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * PG 결제 콜백을 처리하는 Webhook 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentWebhookV1Controller(
    private val paymentFacade: PaymentFacade,
) : PaymentWebhookV1ApiSpec {
    private val logger = LoggerFactory.getLogger(PaymentWebhookV1Controller::class.java)

    /**
     * PG 결제 콜백 수신
     * - orderId로 결제 조회
     * - PG에서 실제 트랜잭션 정보를 조회하여 처리
     */
    @PostMapping("/callback")
    override fun handleCallback(
        @RequestBody request: PaymentWebhookV1Request.Callback,
    ): ApiResponse<Unit> {
        logger.info(
            "PG 콜백 수신 - orderId: {}, externalPaymentKey: {}",
            request.orderId,
            request.externalPaymentKey,
        )

        paymentFacade.processCallback(
            PaymentCriteria.ProcessCallback(
                orderId = request.orderId,
                externalPaymentKey = request.externalPaymentKey,
            ),
        )

        logger.info("PG 콜백 처리 완료 - orderId: {}", request.orderId)
        return ApiResponse.success(Unit)
    }
}
