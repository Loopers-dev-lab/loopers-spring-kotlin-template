package com.loopers.domain.payment

import com.loopers.infrastructure.payment.client.CardTypeDto
import com.loopers.infrastructure.payment.client.PgClient
import com.loopers.infrastructure.payment.client.PgPaymentRequest
import com.loopers.infrastructure.payment.client.TransactionStatusDto
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val pgClient: PgClient,
    @Value("\${pg.callback-url:http://localhost:8080}") private val callbackBaseUrl: String,
) {
    private val logger = LoggerFactory.getLogger(PaymentService::class.java)

    @Transactional
    fun createPayment(
        userId: Long,
        orderId: Long,
        paymentMethod: PaymentMethod,
        amount: Long,
        cardType: String? = null,
        cardNo: String? = null,
    ): Payment {
        val payment = Payment(
            userId = userId,
            orderId = orderId,
            paymentMethod = paymentMethod,
            amount = amount,
            cardType = cardType,
            cardNo = cardNo,
        )
        return paymentRepository.save(payment)
    }

    @Retry(name = "pgRetry", fallbackMethod = "requestPgPaymentFallback")
    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "requestPgPaymentFallback")
    fun requestPgPayment(payment: Payment): String {
        if (payment.cardType == null || payment.cardNo == null) {
            throw CoreException(ErrorType.BAD_REQUEST, "카드 결제 시 카드 정보는 필수입니다.")
        }

        // CardTypeDto를 안전하게 파싱
        val cardType = try {
            CardTypeDto.valueOf(payment.cardType)
        } catch (e: IllegalArgumentException) {
            logger.warn("유효하지 않은 카드 타입: ${payment.cardType}, 허용된 값: ${CardTypeDto.values().joinToString()}")
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "유효하지 않은 카드 타입입니다: ${payment.cardType}. 허용된 값: ${CardTypeDto.values().joinToString()}",
            )
        }

        val request = PgPaymentRequest(
            orderId = payment.orderId.toString(),
            cardType = cardType,
            cardNo = payment.cardNo,
            amount = payment.amount,
            callbackUrl = "$callbackBaseUrl/api/v1/payments/callback",
        )

        logger.info("PG 결제 요청: orderId=${payment.orderId}, amount=${payment.amount}")

        val response = pgClient.requestPayment(
            userId = payment.userId.toString(),
            request = request,
        )

        if (!response.success || response.data == null) {
            val errorMessage = response.error?.message ?: "PG 결제 요청 실패"
            logger.error("PG 결제 요청 실패: $errorMessage")
            throw CoreException(ErrorType.INTERNAL_ERROR, errorMessage)
        }

        logger.info("PG 결제 요청 성공: transactionKey=${response.data.transactionKey}")
        return response.data.transactionKey
    }

    private fun requestPgPaymentFallback(payment: Payment, throwable: Throwable): String {
        logger.error("PG 결제 요청 실패 (Fallback 실행): orderId=${payment.orderId}, error=${throwable.message}", throwable)
        throw CoreException(
            ErrorType.INTERNAL_ERROR,
            "결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.",
        )
    }

    @Transactional
    fun updatePaymentWithTransaction(paymentId: Long, transactionKey: String) {
        val payment = paymentRepository.findById(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다.")

        payment.updateTransactionKey(transactionKey)
        paymentRepository.save(payment)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun savePaymentFailure(paymentId: Long, reason: String) {
        val payment = paymentRepository.findById(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다.")

        payment.fail(reason)
        paymentRepository.save(payment)
        logger.warn("결제 실패 저장 완료: paymentId=$paymentId, reason=$reason")
    }

    @Retry(name = "pgRetry")
    @CircuitBreaker(name = "pgCircuit")
    fun checkPaymentStatus(userId: Long, transactionKey: String): TransactionStatusDto {
        logger.info("PG 결제 상태 확인: transactionKey=$transactionKey")

        val response = pgClient.getTransaction(
            userId = userId.toString(),
            transactionKey = transactionKey,
        )

        if (!response.success || response.data == null) {
            val errorMessage = response.error?.message ?: "PG 결제 상태 조회 실패"
            logger.error("PG 결제 상태 조회 실패: $errorMessage")
            throw CoreException(ErrorType.INTERNAL_ERROR, errorMessage)
        }

        return response.data.status
    }

    @Transactional
    fun handlePaymentCallback(transactionKey: String, status: TransactionStatusDto, reason: String?) {
        val payment = paymentRepository.findByTransactionKey(transactionKey)
            ?: throw CoreException(ErrorType.NOT_FOUND, "거래 키에 해당하는 결제를 찾을 수 없습니다: $transactionKey")

        when (status) {
            TransactionStatusDto.SUCCESS -> {
                payment.complete(reason)
                logger.info("결제 완료: transactionKey=$transactionKey, orderId=${payment.orderId}")
            }
            TransactionStatusDto.FAILED -> {
                payment.fail(reason ?: "결제 실패")
                logger.warn("결제 실패: transactionKey=$transactionKey, reason=$reason")
            }
            TransactionStatusDto.PENDING -> {
                logger.info("결제 대기 중: transactionKey=$transactionKey")
            }
        }

        paymentRepository.save(payment)
    }

    @Transactional
    fun syncPaymentStatus(payment: Payment) {
        if (payment.transactionKey == null) {
            logger.warn("거래 키가 없는 결제는 상태 동기화를 건너뜁니다: paymentId=${payment.id}")
            return
        }

        try {
            val status = checkPaymentStatus(payment.userId, payment.transactionKey!!)
            handlePaymentCallback(payment.transactionKey!!, status, null)
        } catch (e: Exception) {
            logger.error("결제 상태 동기화 실패: paymentId=${payment.id}, transactionKey=${payment.transactionKey}", e)
        }
    }

    fun timeoutPendingPayments() {
        val pendingPayments = paymentRepository.findPendingPaymentsOlderThan(10)
        logger.info("타임아웃 대상 결제 건수: ${pendingPayments.size}")

        pendingPayments.forEach { payment ->
            try {
                // 각 결제를 별도 트랜잭션에서 처리하여 DB 연결 고갈 방지 및 개별 실패 격리
                processTimeoutForSinglePayment(payment)
            } catch (e: Exception) {
                logger.error("결제 타임아웃 처리 실패: paymentId=${payment.id}", e)
                // 한 건의 실패가 전체 배치를 중단하지 않도록 함
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processTimeoutForSinglePayment(payment: Payment) {
        // PG에서 상태를 한 번 더 확인 (외부 호출은 트랜잭션 밖에서 처리하는 것이 이상적이지만,
        // 여기서는 상태 동기화와 타임아웃 처리를 원자적으로 수행)
        if (payment.transactionKey != null) {
            try {
                syncPaymentStatus(payment)
            } catch (e: Exception) {
                logger.error("결제 상태 동기화 중 오류 발생: paymentId=${payment.id}", e)
                // 동기화 실패 시 타임아웃 처리
                payment.timeout()
                paymentRepository.save(payment)
            }
        } else {
            // 거래 키가 없으면 바로 타임아웃
            payment.timeout()
            paymentRepository.save(payment)
        }
    }

    fun getPaymentByTransactionKey(transactionKey: String): Payment {
        return paymentRepository.findByTransactionKey(transactionKey)
            ?: throw CoreException(ErrorType.NOT_FOUND, "거래 키에 해당하는 결제를 찾을 수 없습니다.")
    }

    fun getPaymentsByOrderId(orderId: Long): List<Payment> {
        return paymentRepository.findByOrderId(orderId)
    }
}
