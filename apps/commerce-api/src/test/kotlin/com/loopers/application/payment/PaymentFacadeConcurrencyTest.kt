package com.loopers.application.payment

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.domain.point.PointAccount
import com.loopers.domain.point.PointAccountRepository
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 결제 동시성 통합 테스트
 * - 콜백과 스케줄러가 동시에 같은 결제를 처리하려 할 때
 * - 낙관적 락으로 하나만 성공하고 나머지는 실패
 */
@SpringBootTest
@DisplayName("결제 동시성 통합 테스트")
class PaymentFacadeConcurrencyTest @Autowired constructor(
    private val paymentFacade: PaymentFacade,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @MockkBean
    private lateinit var pgClient: PgClient

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Nested
    @DisplayName("processInProgressPayment")
    inner class ProcessInProgressPayment {

        @Test
        @DisplayName("동시에 같은 결제를 처리하면 낙관적 락으로 하나만 성공한다")
        fun `concurrent payment processing results in only one success due to optimistic lock`() {
            // given
            val payment = createInProgressPayment()
            val successTransaction = createTransaction(
                transactionKey = payment.externalPaymentKey!!,
                paymentId = payment.id,
                status = PgTransactionStatus.SUCCESS,
            )

            every { pgClient.findTransaction(payment.externalPaymentKey!!) } returns successTransaction

            val threadCount = 5
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)

            val successCount = AtomicInteger(0)
            val optimisticLockFailureCount = AtomicInteger(0)
            val unexpectedExceptions = ConcurrentLinkedQueue<Throwable>()

            // when - 5개 스레드가 동시에 같은 결제를 처리 시도
            repeat(threadCount) {
                executor.submit {
                    try {
                        paymentFacade.processInProgressPayment(payment.id)
                        successCount.incrementAndGet()
                    } catch (e: ObjectOptimisticLockingFailureException) {
                        optimisticLockFailureCount.incrementAndGet()
                    } catch (e: Exception) {
                        // 예기치 않은 예외는 별도 추적
                        unexpectedExceptions.add(e)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // then - 하나만 성공하고 나머지는 실패 또는 AlreadyProcessed
            assertAll(
                { assertThat(successCount.get()).isEqualTo(1) },
                { assertThat(optimisticLockFailureCount.get()).isEqualTo(threadCount - 1) },
                {
                    assertThat(unexpectedExceptions)
                        .withFailMessage {
                            "예상치 못한 예외 발생: ${unexpectedExceptions.map { it.message }}"
                        }
                        .isEmpty()
                },
            )
            val finalPayment = paymentRepository.findById(payment.id)!!
            assertThat(finalPayment.status).isEqualTo(PaymentStatus.PAID)
        }
    }

// ===========================================
// 헬퍼 메서드
// ===========================================

    private fun createProduct(
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))
        val product = Product.create(
            name = "테스트 상품",
            price = price,
            brand = brand,
        )
        val savedProduct = productRepository.save(product)
        stockRepository.save(Stock.create(savedProduct.id, stockQuantity))
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }

    private fun createPointAccount(
        userId: Long = 1L,
        balance: Money = Money.ZERO_KRW,
    ): PointAccount {
        val account = PointAccount.of(userId, balance)
        return pointAccountRepository.save(account)
    }

    private fun createInProgressPayment(
        userId: Long = 1L,
    ): Payment {
        val product = createProduct(price = Money.krw(10000), stockQuantity = 100)
        createPointAccount(userId = userId, balance = Money.krw(100000))

        val order = Order.place(userId)
        order.addOrderItem(
            productId = product.id,
            quantity = 1,
            productName = "테스트 상품",
            unitPrice = Money.krw(10000),
        )
        val savedOrder = orderRepository.save(order)

        val totalAmount = savedOrder.totalAmount
        val usedPoint = Money.krw(5000)
        val paidAmount = totalAmount - usedPoint

        val payment = Payment.of(
            orderId = savedOrder.id,
            userId = userId,
            totalAmount = totalAmount,
            usedPoint = usedPoint,
            paidAmount = paidAmount,
            status = PaymentStatus.IN_PROGRESS,
            cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456"),
            externalPaymentKey = "tx_concurrent_test",
            attemptedAt = Instant.now(),
        )
        return paymentRepository.save(payment)
    }

    private fun createTransaction(
        transactionKey: String,
        paymentId: Long,
        status: PgTransactionStatus,
        failureReason: String? = null,
    ): PgTransaction {
        return PgTransaction(
            transactionKey = transactionKey,
            paymentId = paymentId,
            status = status,
            failureReason = failureReason,
        )
    }
}
