package com.loopers.interfaces.api.payment

import com.loopers.application.order.OrderFacade
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * PG 결제 콜백을 처리하는 Webhook 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentWebhookController(
    private val paymentService: PaymentService,
    private val orderFacade: OrderFacade,
) {
    private val logger = LoggerFactory.getLogger(PaymentWebhookController::class.java)

    /**
     * PG 콜백 수신
     * - transactionKey로 결제 조회
     * - 이미 처리된 경우 200 OK 반환 (멱등성)
     * - IN_PROGRESS인 경우 결과 처리
     */
    @PostMapping("/callback")
    fun handleCallback(
        @RequestBody request: PaymentCallbackRequest,
    ): ResponseEntity<PaymentCallbackResponse> {
        logger.info(
            "PG 콜백 수신 - transactionKey: {}, orderId: {}, status: {}",
            request.transactionKey,
            request.orderId,
            request.status,
        )

        // 1. transactionKey로 결제 조회
        val payment = paymentService.findByExternalPaymentKey(request.transactionKey)

        if (payment == null) {
            logger.warn("존재하지 않는 transactionKey - {}", request.transactionKey)
            return ResponseEntity.ok(
                PaymentCallbackResponse.error("결제를 찾을 수 없습니다"),
            )
        }

        // 2. 이미 처리된 결제인지 확인 (멱등성)
        when (payment.status) {
            PaymentStatus.PAID -> {
                logger.info("이미 성공 처리된 결제 - paymentId: {}", payment.id)
                return ResponseEntity.ok(PaymentCallbackResponse.alreadyProcessed())
            }

            PaymentStatus.FAILED -> {
                logger.info("이미 실패 처리된 결제 - paymentId: {}", payment.id)
                return ResponseEntity.ok(PaymentCallbackResponse.alreadyProcessed())
            }

            PaymentStatus.PENDING -> {
                logger.warn("PENDING 상태의 결제에 콜백 수신 - paymentId: {}", payment.id)
                return ResponseEntity.ok(
                    PaymentCallbackResponse.error("결제가 아직 시작되지 않았습니다"),
                )
            }

            PaymentStatus.IN_PROGRESS -> {
                // 정상 처리 대상
            }
        }

        // 3. 결제 결과 처리
        return try {
            orderFacade.handlePaymentResult(
                paymentId = payment.id,
                isSuccess = request.isSuccess(),
                transactionKey = request.transactionKey,
                reason = request.reason,
            )

            logger.info(
                "PG 콜백 처리 완료 - paymentId: {}, status: {}",
                payment.id,
                if (request.isSuccess()) "PAID" else "FAILED",
            )

            ResponseEntity.ok(PaymentCallbackResponse.success())
        } catch (e: Exception) {
            logger.error("PG 콜백 처리 실패 - paymentId: {}", payment.id, e)
            ResponseEntity.ok(PaymentCallbackResponse.error(e.message ?: "처리 중 오류가 발생했습니다"))
        }
    }
}
