package com.loopers.application.payment

import com.loopers.application.order.PaymentResultHandler
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.Payment
import com.loopers.domain.order.PaymentService
import com.loopers.infrastructure.pg.PgClient
import com.loopers.infrastructure.pg.PgException
import com.loopers.infrastructure.pg.PgTransactionStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZonedDateTime

/**
 * 결제 관련 비즈니스 로직을 오케스트레이션하는 Facade
 *
 * - IN_PROGRESS 결제 조회
 * - PG 상태 확인 후 성공/실패 처리
 * - 보상 트랜잭션 관리
 */
@Component
class PaymentFacade(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val paymentResultHandler: PaymentResultHandler,
    private val pgClient: PgClient,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(PaymentFacade::class.java)

    companion object {
        private const val FORCE_FAIL_THRESHOLD_MINUTES = 5L
    }

    /**
     * PG 상태 조회 결과
     */
    sealed class PgQueryResult {
        data class Success(val transactionKey: String) : PgQueryResult()
        data class Failed(val reason: String) : PgQueryResult()
        data object Pending : PgQueryResult()
        data object NotFound : PgQueryResult()
        data class QueryFailed(val reason: String) : PgQueryResult()
    }

    /**
     * IN_PROGRESS 상태의 결제 목록을 조회합니다.
     *
     * @param threshold 조회 기준 시간 (이전에 업데이트된 결제만 조회)
     * @return IN_PROGRESS 상태의 Payment 목록
     */
    fun findInProgressPayments(threshold: ZonedDateTime): List<Payment> {
        return paymentService.findInProgressPayments(threshold)
    }

    /**
     * IN_PROGRESS 상태의 결제를 처리합니다.
     * PG 상태를 조회하고 결과에 따라 성공/실패 처리합니다.
     *
     * @param payment 처리할 결제
     */
    fun processInProgressPayment(payment: Payment) {
        // PG 상태 조회 (트랜잭션 외부)
        val pgResult = queryPgStatus(payment.userId, payment.orderId.toString())

        // 결과에 따라 처리 (트랜잭션 내부)
        transactionTemplate.execute { _ ->
            when (pgResult) {
                is PgQueryResult.Success -> {
                    logger.info(
                        "PG 결제 성공 확인 - paymentId: {}, transactionKey: {}",
                        payment.id,
                        pgResult.transactionKey,
                    )
                    paymentResultHandler.handlePaymentSuccess(payment.id, pgResult.transactionKey)
                }

                is PgQueryResult.Failed -> {
                    logger.info(
                        "PG 결제 실패 확인 - paymentId: {}, reason: {}",
                        payment.id,
                        pgResult.reason,
                    )
                    val orderItems = getOrderItems(payment.orderId)
                    paymentResultHandler.handlePaymentFailure(payment.id, pgResult.reason, orderItems)
                }

                is PgQueryResult.Pending -> {
                    // 5분 초과 시 강제 실패
                    val forceFailThreshold = ZonedDateTime.now().minusMinutes(FORCE_FAIL_THRESHOLD_MINUTES)
                    if (payment.createdAt.isBefore(forceFailThreshold)) {
                        logger.warn(
                            "결제 강제 실패 처리 - paymentId: {}, createdAt: {}, threshold: {}",
                            payment.id,
                            payment.createdAt,
                            forceFailThreshold,
                        )
                        val orderItems = getOrderItems(payment.orderId)
                        paymentResultHandler.handlePaymentFailure(
                            paymentId = payment.id,
                            reason = "결제 시간 초과 (5분)",
                            orderItems = orderItems,
                        )
                    } else {
                        logger.debug(
                            "PG 결제 아직 처리 중 - paymentId: {}, createdAt: {}",
                            payment.id,
                            payment.createdAt,
                        )
                    }
                }

                is PgQueryResult.NotFound -> {
                    // PG에 기록이 없는 경우 - 요청이 도달하지 않은 것으로 판단
                    logger.warn(
                        "PG에 결제 기록 없음 - paymentId: {}, orderId: {}",
                        payment.id,
                        payment.orderId,
                    )
                    val orderItems = getOrderItems(payment.orderId)
                    paymentResultHandler.handlePaymentFailure(
                        paymentId = payment.id,
                        reason = "PG에 결제 기록 없음",
                        orderItems = orderItems,
                    )
                }

                is PgQueryResult.QueryFailed -> {
                    // PG 조회 실패 - 다음 스케줄에 재시도
                    logger.warn(
                        "PG 상태 조회 실패 - paymentId: {}, reason: {}",
                        payment.id,
                        pgResult.reason,
                    )
                }
            }
        }
    }

    /**
     * PG 상태를 조회합니다.
     */
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

    /**
     * 주문 상품 정보를 조회합니다 (재고 복구용).
     */
    private fun getOrderItems(orderId: Long): List<PaymentResultHandler.OrderItemInfo> {
        val order = orderService.findById(orderId)
        return order.orderItems.map {
            PaymentResultHandler.OrderItemInfo(
                productId = it.productId,
                quantity = it.quantity,
            )
        }
    }
}
