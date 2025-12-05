package com.loopers.domain.payment

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.strategy.PgStrategy
import com.loopers.domain.product.ProductService
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 결제 대사 스케줄러
 *
 * 콜백 누락 케이스를 복구하기 위해 주기적으로 PENDING 상태 주문을 확인하고
 * PG에 실제 상태를 조회하여 동기화합니다.
 *
 * 코루틴을 사용한 병렬 처리로 성능 최적화 (순차 처리 대비 약 10배 빠름)
 */
@Component
class PaymentReconciliationScheduler(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val productService: ProductService,
    private val pgStrategies: List<PgStrategy>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // 코루틴 스코프 (애플리케이션 생명주기와 함께)
    // SupervisorJob: 하나의 작업 실패가 다른 작업에 영향을 주지 않음
    private val coroutineScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob()
    )

    /**
     * 1분마다 실행
     * 10분 이상 PENDING 상태인 주문을 찾아서 PG 상태를 조회하고 복구
     */
    @Scheduled(fixedDelay = 60000) // 1분 = 60,000ms
    fun reconcileStaleOrders() = runBlocking {
        val cutoffTime = LocalDateTime.now().minusMinutes(10)

        // PENDING 상태가 10분 이상인 주문들 조회
        val staleOrders = orderRepository.findByStatusAndCreatedAtBefore(
            OrderStatus.PENDING,
            cutoffTime
        )

        if (staleOrders.isEmpty()) {
            return@runBlocking
        }

        logger.info("Found ${staleOrders.size} stale orders to reconcile")

        // 10개씩 묶어서 병렬 처리
        // 너무 많은 동시 요청은 PG API에 부담을 줄 수 있으므로 적절히 제한
        staleOrders.chunked(10).forEach { chunk ->
            chunk.map { order ->
                async(Dispatchers.IO) {
                    try {
                        reconcileOrder(order)
                    } catch (e: Exception) {
                        logger.error("Failed to reconcile order: ${order.id}", e)
                    }
                }
            }.awaitAll()  // 현재 청크의 모든 작업 완료 대기
        }

        logger.info("Reconciliation completed for ${staleOrders.size} orders")
    }

    /**
     * 개별 주문 대사 처리
     *
     * 1. 해당 주문의 Payment 조회
     * 2. PENDING 상태가 아니면 스킵 (이미 처리됨)
     * 3. PG에 실제 상태 확인
     * 4. 상태에 따라 처리:
     *    - SUCCESS: 재고 차감 + 주문 완료
     *    - FAILED: 주문 실패 처리
     *    - PENDING: 계속 대기
     */
    @Transactional
    fun reconcileOrder(order: Order) {
        // 1. 해당 주문의 Payment 조회
        val payments = paymentRepository.findByOrderId(order.id!!)
        val pendingPayment = payments.firstOrNull { it.status == PaymentStatus.PENDING }

        if (pendingPayment == null) {
            // Payment가 없거나 모두 실패 → Order도 실패 처리
            order.fail()
            logger.info("No pending payment found. Order marked as FAILED: ${order.id}")
            return
        }

        if (pendingPayment.transactionKey == null) {
            // PG 요청 자체가 실패한 케이스
            pendingPayment.markAsFailed("PG 요청 실패")
            order.fail()
            logger.info("Payment request failed. Order marked as FAILED: ${order.id}")
            return
        }

        // 2. PG에 실제 상태 확인
        try {
            val pgStrategy = pgStrategies.firstOrNull {
                it.supports(pendingPayment.paymentMethod)
            } ?: throw IllegalStateException("No PG strategy found for ${pendingPayment.paymentMethod}")

            val userId = order.memberId
            val pgStatus = pgStrategy.getPaymentStatus(userId, pendingPayment.transactionKey!!)

            when (pgStatus.status) {
                "SUCCESS" -> {
                    // 콜백 누락 케이스 → 수동 복구
                    try {
                        productService.decreaseStockByOrder(order)
                        pendingPayment.markAsSuccess()
                        order.complete()
                        logger.info("Recovered payment: orderId=${order.id}, transactionKey=${pendingPayment.transactionKey}")
                    } catch (e: Exception) {
                        // 재고 부족 시 PG 취소 필요
                        pendingPayment.markAsFailed("재고 부족: ${e.message}")
                        order.fail()
                        logger.error("Out of stock during reconciliation: orderId=${order.id}")
                        // TODO: PG 취소 API 호출 및 CS 팀 알림
                    }
                }
                "FAILED" -> {
                    pendingPayment.markAsFailed(pgStatus.failureReason ?: "알 수 없는 오류")
                    order.fail()
                    logger.info("Payment failed during reconciliation: orderId=${order.id}")
                }
                else -> {
                    // 아직 PENDING → 계속 대기
                    logger.debug("Payment still pending: orderId=${order.id}")
                }
            }
        } catch (e: Exception) {
            // PG 조회도 실패 → 다음 주기에 재시도
            logger.warn("Failed to query PG status: orderId=${order.id}", e)
        }
    }

    /**
     * 애플리케이션 종료 시 코루틴 스코프 정리
     * 진행 중인 작업을 취소하고 리소스를 해제합니다.
     */
    @PreDestroy
    fun cleanup() {
        logger.info("Shutting down PaymentReconciliationScheduler coroutine scope")
        coroutineScope.cancel()
    }
}
