package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentFacade
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * IN_PROGRESS 상태로 남아있는 결제를 주기적으로 확인하여 최종 상태로 전환하는 스케줄러
 *
 * - 1분마다 실행
 * - 모든 IN_PROGRESS 결제를 조회
 * - PaymentFacade에 위임하여 PG 상태 확인 및 처리
 * - 5분 타임아웃은 Payment 도메인에서 자동 처리
 */
@Component
class PaymentScheduler(
    private val paymentFacade: PaymentFacade,
    meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(PaymentScheduler::class.java)

    init {
        Gauge.builder(METRIC_IN_PROGRESS_COUNT) { paymentFacade.findInProgressPayments().size.toDouble() }
            .description("Number of payments in IN_PROGRESS status")
            .register(meterRegistry)
    }

    /**
     * 매 1분마다 실행
     */
    @Scheduled(fixedRate = 60_000)
    fun checkInProgressPayments() {
        val inProgressPayments = paymentFacade.findInProgressPayments()

        if (inProgressPayments.isEmpty()) {
            return
        }

        logger.info(
            "IN_PROGRESS 결제 상태 확인 시작 - count: {}",
            inProgressPayments.size,
        )

        var processedCount = 0
        var skippedCount = 0

        for (payment in inProgressPayments) {
            try {
                paymentFacade.processInProgressPayment(payment.id)
                processedCount++
            } catch (e: PgRequestNotReachedException) {
                // PG 연결 실패 - 다음 스케줄에 재시도
                logger.warn(
                    "PG 연결 실패, 다음 스케줄에 재시도 - paymentId: {}",
                    payment.id,
                )
                skippedCount++
            } catch (e: ObjectOptimisticLockingFailureException) {
                // 콜백과 동시 처리로 인한 충돌 - 정상 케이스이므로 skip
                logger.debug(
                    "결제 상태 확인 중 낙관적 락 충돌 - paymentId: {} (이미 다른 곳에서 처리됨)",
                    payment.id,
                )
                skippedCount++
            } catch (e: Exception) {
                logger.error(
                    "결제 상태 확인 중 예상치 못한 오류 - paymentId: {}",
                    payment.id,
                    e,
                )
                skippedCount++
            }
        }

        logger.info(
            "IN_PROGRESS 결제 상태 확인 완료 - processed: {}, skipped: {}",
            processedCount,
            skippedCount,
        )
    }

    companion object {
        private const val METRIC_IN_PROGRESS_COUNT = "payment_in_progress_count"
    }
}
