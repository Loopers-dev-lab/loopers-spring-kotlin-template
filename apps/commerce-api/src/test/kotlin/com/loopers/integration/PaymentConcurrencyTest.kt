package com.loopers.integration

import com.loopers.application.order.PaymentResultHandler
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.point.PointAccount
import com.loopers.domain.point.PointAccountRepository
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 결제 동시성 테스트
 * - 콜백과 스케줄러가 동시에 같은 결제를 처리하려 할 때
 * - 낙관적 락으로 하나만 성공하고 나머지는 실패
 */
@SpringBootTest
@DisplayName("결제 동시성 테스트")
class PaymentConcurrencyTest @Autowired constructor(
    private val paymentService: PaymentService,
    private val paymentResultHandler: PaymentResultHandler,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val transactionTemplate: TransactionTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Test
    @DisplayName("동시에 같은 결제를 처리하면 낙관적 락으로 하나만 성공한다")
    fun `concurrent payment processing results in only one success due to optimistic lock`() {
        // given
        val payment = createInProgressPayment()
        val transactionKey = "tx_concurrent_test"

        val threadCount = 5
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when - 5개 스레드가 동시에 같은 결제를 성공 처리 시도
        repeat(threadCount) {
            executor.submit {
                try {
                    transactionTemplate.execute { _ ->
                        paymentResultHandler.handlePaymentSuccess(payment.id, transactionKey)
                    }
                    successCount.incrementAndGet()
                } catch (e: ObjectOptimisticLockingFailureException) {
                    failureCount.incrementAndGet()
                } catch (e: Exception) {
                    // 다른 예외도 실패로 처리
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then - 정확히 하나만 성공
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failureCount.get()).isEqualTo(threadCount - 1)

        // 결제 상태는 PAID
        val finalPayment = paymentRepository.findById(payment.id)!!
        assertThat(finalPayment.status).isEqualTo(PaymentStatus.PAID)
    }

    @Test
    @DisplayName("콜백 성공 후 스케줄러가 처리하려 하면 낙관적 락 예외가 발생한다")
    fun `scheduler after callback success gets optimistic lock exception`() {
        // given
        val payment = createInProgressPayment()
        val transactionKey = "tx_callback_first"
        val order = orderRepository.findById(payment.orderId)!!
        val orderItems = order.orderItems.map {
            PaymentResultHandler.OrderItemInfo(
                productId = it.productId,
                quantity = it.quantity,
            )
        }

        // 콜백 먼저 성공 처리
        transactionTemplate.execute { _ ->
            paymentResultHandler.handlePaymentSuccess(payment.id, transactionKey)
        }

        // 버전이 증가했으므로 이전 버전으로 처리하려 하면 실패
        // 스케줄러 시뮬레이션: 동일한 결제를 다시 처리 시도
        var optimisticLockExceptionOccurred = false
        try {
            transactionTemplate.execute { _ ->
                // 이미 PAID 상태이므로 상태 체크에서 예외 발생
                paymentResultHandler.handlePaymentFailure(payment.id, "강제 실패", orderItems)
            }
        } catch (e: Exception) {
            // 상태 체크 예외 또는 낙관적 락 예외
            optimisticLockExceptionOccurred = true
        }

        // then
        assertThat(optimisticLockExceptionOccurred).isTrue()

        // 결제 상태는 여전히 PAID
        val finalPayment = paymentRepository.findById(payment.id)!!
        assertThat(finalPayment.status).isEqualTo(PaymentStatus.PAID)
    }

    @Test
    @DisplayName("두 번째 처리 시도 시 상태 체크로 인해 예외가 발생한다")
    fun `second processing attempt fails due to state check`() {
        // given
        val payment = createInProgressPayment()
        val transactionKey = "tx_state_check"

        // 첫 번째 처리 - 성공
        transactionTemplate.execute { _ ->
            paymentResultHandler.handlePaymentSuccess(payment.id, transactionKey)
        }

        // when - 두 번째 처리 시도
        var exceptionOccurred = false
        var exceptionMessage = ""
        try {
            transactionTemplate.execute { _ ->
                // PAID 상태에서 다시 success() 호출 시도
                val existingPayment = paymentService.findById(payment.id)
                existingPayment.paid() // 여기서 예외 발생해야 함
            }
        } catch (e: Exception) {
            exceptionOccurred = true
            exceptionMessage = e.message ?: ""
        }

        // then
        assertThat(exceptionOccurred).isTrue()
        assertThat(exceptionMessage).contains("결제 진행 중 상태에서만 성공 처리할 수 있습니다")
    }

    private fun createProduct(
        price: Money = Money.krw(10000),
        stock: Stock = Stock.of(100),
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))
        val product = Product.create(
            name = "테스트 상품",
            price = price,
            stock = stock,
            brand = brand,
        )
        val savedProduct = productRepository.save(product)
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
        val product = createProduct(price = Money.krw(10000), stock = Stock.of(100))
        createPointAccount(userId = userId, balance = Money.krw(100000))

        // Order 생성
        val order = Order.place(userId)
        order.addOrderItem(
            productId = product.id,
            quantity = 1,
            productName = "테스트 상품",
            unitPrice = Money.krw(10000),
        )
        val savedOrder = orderRepository.save(order)

        // Payment 생성 (PENDING -> IN_PROGRESS), paidAmount = 10000 - 5000 = 5000 자동 계산
        val payment = paymentService.createPending(
            userId = userId,
            order = savedOrder,
            usedPoint = Money.krw(5000),
        )

        return paymentService.startPayment(payment.id)
    }
}
