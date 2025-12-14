package com.loopers.interfaces.scheduler

import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.payment.PaymentService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

/**
 * 결제 상태 동기화 스케줄러
 *
 * PG사와의 통신 실패로 인해 PENDING 상태로 남아있는 결제를
 * 주기적으로 PG사에 조회하여 실제 승인된 결제를 복구합니다.
 *
 * 실행 주기: 5분마다
 * 대상: 생성 후 5분이 지난 PENDING 상태의 결제
 */
@Component
class PaymentStatusSyncScheduler(
    private val paymentService: PaymentService,
    private val paymentFacade: PaymentFacade,
) {

    private val log = LoggerFactory.getLogger(PaymentStatusSyncScheduler::class.java)

    // TODO: 결제 상태가 N분이상 'PENDING'인 경우 실패 처리하는 스케줄러 추가

    @Scheduled(fixedDelay = 300000) // 5분마다
    fun syncPendingPayments() {
        val fiveMinutesAgo = ZonedDateTime.now().minusMinutes(5)
        val pendingPayments = paymentService.findPending(fiveMinutesAgo)

        log.info("결제 상태 동기화 시작: ${pendingPayments.size}건")

        pendingPayments.forEach { payment ->
            try {
                paymentFacade.syncPendingPayment(payment)
            } catch (e: Exception) {
                log.error("결제 상태 동기화 중 오류 발생: orderId=${payment.orderId}", e)
            }
        }

        log.info("결제 상태 동기화 완료: ${pendingPayments.size}건 처리")
    }
}
