package com.loopers.infrastructure.payment.scheduler

import com.loopers.domain.payment.PaymentService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PaymentStatusScheduler(
    private val paymentService: PaymentService,
) {
    private val logger = LoggerFactory.getLogger(PaymentStatusScheduler::class.java)

    /**
     * 10분 이상 PENDING 상태인 결제 건들을 확인하고 타임아웃 처리합니다.
     * 5분마다 실행됩니다.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000) // 5분마다, 시작 1분 후
    fun checkPendingPayments() {
        try {
            logger.info("결제 상태 확인 스케줄러 시작")
            paymentService.timeoutPendingPayments()
            logger.info("결제 상태 확인 스케줄러 완료")
        } catch (e: Exception) {
            logger.error("결제 상태 확인 스케줄러 실행 중 오류 발생", e)
        }
    }
}
