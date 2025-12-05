package com.loopers.infrastructure.payment

import com.loopers.application.order.PaymentResultHandler
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.PaymentService
import com.loopers.infrastructure.pg.PgClient
import com.loopers.infrastructure.pg.PgException
import com.loopers.infrastructure.pg.PgTransactionStatus
import org.slf4j.LoggerFactory
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZonedDateTime

/**
 * IN_PROGRESS 상태로 남아있는 결제를 주기적으로 확인하여 최종 상태로 전환하는 스케줄러
 *
 * - 1분마다 실행
 * - 1분 이상 IN_PROGRESS인 결제를 조회
 * - PG에 상태 조회 후 결과에 따라 처리
 * - 5분 초과 시 강제 실패 처리
 */
@Component
class PaymentStatusScheduler(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val paymentResultHandler: PaymentResultHandler,
    private val pgClient: PgClient,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(PaymentStatusScheduler::class.java)

    companion object {
        private const val CHECK_THRESHOLD_MINUTES = 1L
        private const val FORCE_FAIL_THRESHOLD_MINUTES = 5L
    }

    /**
     * 매 1분마다 실행
     */
    @Scheduled(fixedRate = 60_000)
    fun checkInProgressPayments() {
        val threshold = ZonedDateTime.now().minusMinutes(CHECK_THRESHOLD_MINUTES)
        val inProgressPayments = paymentService.findInProgressPayments(threshold)

        if (inProgressPayments.isEmpty()) {
            return
        }

        logger.info(
            "IN_PROGRESS 결제 상태 확인 시작 - count: {}, threshold: {}",
            inProgressPayments.size,
            threshold,
        )

        var processedCount = 0
        var skippedCount = 0

        for (payment in inProgressPayments) {
            try {
                processPayment(payment.id, payment.userId, payment.orderId, payment.createdAt)
                processedCount++
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
            }
        }

        logger.info(
            "IN_PROGRESS 결제 상태 확인 완료 - processed: {}, skipped: {}",
            processedCount,
            skippedCount,
        )
    }

    private fun processPayment(
        paymentId: Long,
        userId: Long,
        orderId: Long,
        createdAt: ZonedDateTime,
    ) {
        // PG 상태 조회 (트랜잭션 외부)
        val pgResult = queryPgStatus(userId, orderId.toString())

        // 결과에 따라 처리
        transactionTemplate.execute { _ ->
            when (pgResult) {
                is PgQueryResult.Success -> {
                    logger.info(
                        "PG 결제 성공 확인 - paymentId: {}, transactionKey: {}",
                        paymentId,
                        pgResult.transactionKey,
                    )
                    paymentResultHandler.handlePaymentSuccess(paymentId, pgResult.transactionKey)
                }

                is PgQueryResult.Failed -> {
                    logger.info(
                        "PG 결제 실패 확인 - paymentId: {}, reason: {}",
                        paymentId,
                        pgResult.reason,
                    )
                    val orderItems = getOrderItems(orderId)
                    paymentResultHandler.handlePaymentFailure(paymentId, pgResult.reason, orderItems)
                }

                is PgQueryResult.Pending -> {
                    // 5분 초과 시 강제 실패
                    val forceFailThreshold = ZonedDateTime.now().minusMinutes(FORCE_FAIL_THRESHOLD_MINUTES)
                    if (createdAt.isBefore(forceFailThreshold)) {
                        logger.warn(
                            "결제 강제 실패 처리 - paymentId: {}, createdAt: {}, threshold: {}",
                            paymentId,
                            createdAt,
                            forceFailThreshold,
                        )
                        val orderItems = getOrderItems(orderId)
                        paymentResultHandler.handlePaymentFailure(
                            paymentId = paymentId,
                            reason = "결제 시간 초과 (5분)",
                            orderItems = orderItems,
                        )
                    } else {
                        logger.debug(
                            "PG 결제 아직 처리 중 - paymentId: {}, createdAt: {}",
                            paymentId,
                            createdAt,
                        )
                    }
                }

                is PgQueryResult.NotFound -> {
                    // PG에 기록이 없는 경우 - 요청이 도달하지 않은 것으로 판단
                    logger.warn(
                        "PG에 결제 기록 없음 - paymentId: {}, orderId: {}",
                        paymentId,
                        orderId,
                    )
                    val orderItems = getOrderItems(orderId)
                    paymentResultHandler.handlePaymentFailure(
                        paymentId = paymentId,
                        reason = "PG에 결제 기록 없음",
                        orderItems = orderItems,
                    )
                }

                is PgQueryResult.QueryFailed -> {
                    // PG 조회 실패 - 다음 스케줄에 재시도
                    logger.warn(
                        "PG 상태 조회 실패 - paymentId: {}, reason: {}",
                        paymentId,
                        pgResult.reason,
                    )
                }
            }
        }
    }

    private fun queryPgStatus(userId: Long, orderId: String): PgQueryResult {
        return try {
            val response = pgClient.getPaymentsByOrderId(userId, orderId)
            val latestTransaction = response.transactions.lastOrNull()

            if (latestTransaction == null) {
                PgQueryResult.NotFound
            } else {
                when (latestTransaction.status) {
                    PgTransactionStatus.SUCCESS.name -> {
                        PgQueryResult.Success(latestTransaction.transactionKey)
                    }
                    PgTransactionStatus.FAILED.name -> {
                        PgQueryResult.Failed(latestTransaction.reason ?: "PG 결제 실패")
                    }
                    else -> {
                        PgQueryResult.Pending
                    }
                }
            }
        } catch (e: PgException.BusinessError) {
            if (e.errorCode == "NOT_FOUND") {
                PgQueryResult.NotFound
            } else {
                PgQueryResult.QueryFailed(e.message ?: "PG 비즈니스 에러")
            }
        } catch (e: PgException) {
            PgQueryResult.QueryFailed(e.message ?: "PG 조회 실패")
        }
    }

    private fun getOrderItems(orderId: Long): List<PaymentResultHandler.OrderItemInfo> {
        val order = orderService.findById(orderId)
        return order.orderItems.map {
            PaymentResultHandler.OrderItemInfo(
                productId = it.productId,
                quantity = it.quantity,
            )
        }
    }

    /**
     * PG 상태 조회 결과
     */
    private sealed class PgQueryResult {
        data class Success(val transactionKey: String) : PgQueryResult()
        data class Failed(val reason: String) : PgQueryResult()
        data object Pending : PgQueryResult()
        data object NotFound : PgQueryResult()
        data class QueryFailed(val reason: String) : PgQueryResult()
    }
}
