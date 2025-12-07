package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCriteria
import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.payment.PaymentSortType
import com.loopers.domain.payment.PaymentStatus
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
) {
    private val logger = LoggerFactory.getLogger(PaymentScheduler::class.java)

    /**
     * 매 1분마다 실행
     * - 모든 IN_PROGRESS 결제를 페이지네이션으로 조회하여 처리
     * - pool.size=1 설정으로 동시 실행 방지 (application.yml)
     */
    @Scheduled(fixedRate = 60_000)
    fun checkInProgressPayments() {
        var totalProcessed = 0
        var totalSkipped = 0
        val attemptedIds = mutableSetOf<Long>()

        logger.info("checkInProgressPayments scheduler start")

        // 처리된 결제는 상태가 변경되어 조건에서 제외되므로 항상 page=0으로 조회
        // 스킵된 결제를 중복 시도하지 않기 위해 attemptedIds로 추적
        while (true) {
            val criteria = PaymentCriteria.FindPayments(
                page = 0,
                statuses = listOf(PaymentStatus.IN_PROGRESS),
                sort = PaymentSortType.CREATED_AT_ASC,
            )
            val slice = paymentFacade.findPayments(criteria)

            // 아직 시도하지 않은 결제만 필터링
            val newPayments = slice.content.filter { it.id !in attemptedIds }

            if (newPayments.isEmpty()) {
                break
            }

            for (payment in newPayments) {
                attemptedIds.add(payment.id)
                try {
                    paymentFacade.processInProgressPayment(payment.id)
                    totalProcessed++
                } catch (e: PgRequestNotReachedException) {
                    // PG 연결 실패 - 다음 스케줄에 재시도
                    logger.warn(
                        "PG 연결 실패, 다음 스케줄에 재시도 - paymentId: {}",
                        payment.id,
                    )
                    totalSkipped++
                } catch (e: ObjectOptimisticLockingFailureException) {
                    // 콜백과 동시 처리로 인한 충돌 - 정상 케이스이므로 skip
                    logger.debug(
                        "낙관적 락 충돌 - paymentId: {} (이미 처리됨)",
                        payment.id,
                    )
                    totalSkipped++
                } catch (e: Exception) {
                    logger.error(
                        "예상치 못한 오류 - paymentId: {}",
                        payment.id,
                        e,
                    )
                    totalSkipped++
                }
            }
        }

        if (totalProcessed > 0 || totalSkipped > 0) {
            logger.info(
                "checkInProgressPayments scheduler end - processed: {}, skipped: {}",
                totalProcessed,
                totalSkipped,
            )
        }
    }
}
