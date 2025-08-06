package com.loopers.domain.payment

import com.loopers.application.payment.PaymentProcessor
import com.loopers.domain.order.OrderItemService
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.dto.command.OrderCommand
import com.loopers.domain.order.dto.command.OrderItemCommand.Register.Item
import com.loopers.domain.order.entity.Order
import com.loopers.domain.order.entity.Order.Status.PAYMENT_REQUEST
import com.loopers.domain.payment.dto.command.PaymentCommand
import com.loopers.domain.payment.entity.Payment
import com.loopers.domain.payment.entity.Payment.Method.POINT
import com.loopers.domain.point.Point
import com.loopers.domain.product.entity.Product
import com.loopers.domain.product.entity.ProductOption
import com.loopers.domain.product.entity.ProductStock
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.payment.PaymentJpaRepository
import com.loopers.infrastructure.point.PointJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.product.ProductOptionJpaRepository
import com.loopers.infrastructure.product.ProductStockJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest
class PaymentProcessorIntegrationTest @Autowired constructor(
    private val paymentProcessor: PaymentProcessor,
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val orderItemService: OrderItemService,
    private val productOptionRepository: ProductOptionJpaRepository,
    private val productStockRepository: ProductStockJpaRepository,
    private val productRepository: ProductJpaRepository,
    private val pointRepository: PointJpaRepository,
    private val paymentRepository: PaymentJpaRepository,
    private val orderRepository: OrderJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("결제 처리")
    @Nested
    inner class Process {

        @Test
        fun `정상적으로 결제 처리된다`() {
            // given
            val userId = 1L

            val point = pointRepository.save(Point.create(userId, BigDecimal("10000")))

            val product = productRepository.save(
                Product.create(1L, "상품", "설명", BigDecimal("1000")),
            )

            val option = productOptionRepository.save(
                ProductOption.create(product.id, 1L, "화이트", "M", "노출용이름", BigDecimal("1000")),
            )

            val productStock = productStockRepository.save(
                ProductStock.create(option.id, 10),
            )

            val orderCommand = OrderCommand.RequestOrder(
                userId,
                BigDecimal("1000"),
                BigDecimal("1000"),
                PAYMENT_REQUEST,
                listOf(Item(option.id, 1)),
            )

            val order = orderService.request(orderCommand)
            orderItemService.register(orderCommand.toItemCommand(order.id))

            val payment = paymentService.request(
                PaymentCommand.Request(order.id, POINT).toEntity(BigDecimal("1000")),
            )

            // when
            paymentProcessor.process(PaymentCommand.Process(order.id, payment.id))

            // then
            val updatedPayment = paymentService.get(payment.id)

            assertThat(updatedPayment.status).isEqualTo(Payment.Status.SUCCESS)
        }

        @Test
        fun `포인트가 부족하면 POINT_NOT_ENOUGH 예외가 발생한다`() {
            // given
            val userId = 1L
            pointRepository.save(Point.create(userId, BigDecimal("500")))

            val product = productRepository.save(
                Product.create(1L, "상품", "설명", BigDecimal("1000")),
            )

            val option = productOptionRepository.save(
                ProductOption.create(product.id, 1L, "화이트", "M", "노출용이름", BigDecimal("1000")),
            )

            productStockRepository.save(ProductStock.create(option.id, 10))

            val orderCommand = OrderCommand.RequestOrder(
                userId,
                BigDecimal("1000"),
                BigDecimal("1000"),
                PAYMENT_REQUEST,
                listOf(Item(option.id, 1)),
            )
            val order = orderService.request(orderCommand)
            orderItemService.register(orderCommand.toItemCommand(order.id))

            val payment = paymentService.request(
                PaymentCommand.Request(order.id, POINT).toEntity(BigDecimal("1000")),
            )

            // expect
            val exception = assertThrows<CoreException> {
                paymentProcessor.process(PaymentCommand.Process(order.id, payment.id))
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.POINT_NOT_ENOUGH)
        }

        @Test
        fun `재고가 부족하면 STOCK_NOT_ENOUGH 예외가 발생한다`() {
            // given
            val userId = 1L
            pointRepository.save(Point.create(userId, BigDecimal("10000")))

            val product = productRepository.save(
                Product.create(1L, "상품", "설명", BigDecimal("1000")),
            )

            val option = productOptionRepository.save(
                ProductOption.create(product.id, 1L, "화이트", "M", "노출용이름", BigDecimal("1000")),
            )

            productStockRepository.save(ProductStock.create(option.id, 0))

            val orderCommand = OrderCommand.RequestOrder(
                userId,
                BigDecimal("1000"),
                BigDecimal("1000"),
                PAYMENT_REQUEST,
                listOf(Item(option.id, 1)),
            )
            val order = orderService.request(orderCommand)
            orderItemService.register(orderCommand.toItemCommand(order.id))

            val payment = paymentService.request(
                PaymentCommand.Request(order.id, POINT).toEntity(BigDecimal("1000")),
            )

            // expect
            val exception = assertThrows<CoreException> {
                paymentProcessor.process(PaymentCommand.Process(order.id, payment.id))
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.PRODUCT_STOCK_NOT_ENOUGH)
        }

        @Test
        fun `포인트가 부족하면 결제와 주문은 실패 상태가 되고, 포인트는 차감되지 않는다`() {
            // given
            val userId = 1L

            val point = pointRepository.save(Point.create(userId, BigDecimal("100")))

            val product = productRepository.save(
                Product.create(1L, "상품", "설명", BigDecimal("1000")),
            )

            val option = productOptionRepository.save(
                ProductOption.create(product.id, 1L, "화이트", "M", "노출용이름", BigDecimal("1000")),
            )

            val productStock = productStockRepository.save(ProductStock.create(option.id, 10))

            val orderCommand = OrderCommand.RequestOrder(
                userId,
                BigDecimal("1000"),
                BigDecimal("1000"),
                PAYMENT_REQUEST,
                listOf(Item(option.id, 1)),
            )

            val order = orderService.request(orderCommand)
            orderItemService.register(orderCommand.toItemCommand(order.id))

            val payment = paymentService.request(
                PaymentCommand.Request(order.id, POINT).toEntity(BigDecimal("1000")),
            )

            // when
            assertThrows<CoreException> {
                paymentProcessor.process(PaymentCommand.Process(order.id, payment.id))
            }

            // then
            val reloadedPoint = pointRepository.findById(point.id).get()
            val reloadedPayment = paymentRepository.findById(payment.id).get()
            val reloadedOrder = orderRepository.findById(order.id).get()

            assertThat(reloadedPoint.amount.value).isEqualByComparingTo(BigDecimal(100))
            assertThat(reloadedPayment.status).isEqualTo(Payment.Status.FAILED)
            assertThat(reloadedOrder.status).isEqualTo(Order.Status.ORDER_FAIL)
        }

        @Test
        fun `재고가 부족하면 결제와 주문은 실패 상태가 되고, 재고는 차감되지 않는다`() {
            // given
            val userId = 1L

            val point = pointRepository.save(Point.create(userId, BigDecimal("2000")))

            val product = productRepository.save(
                Product.create(1L, "상품", "설명", BigDecimal("1000")),
            )

            val option = productOptionRepository.save(
                ProductOption.create(product.id, 1L, "화이트", "M", "노출용이름", BigDecimal("1000")),
            )

            val productStock = productStockRepository.save(ProductStock.create(option.id, 0))

            val orderCommand = OrderCommand.RequestOrder(
                userId,
                BigDecimal("1000"),
                BigDecimal("1000"),
                PAYMENT_REQUEST,
                listOf(Item(option.id, 1)),
            )

            val order = orderService.request(orderCommand)
            orderItemService.register(orderCommand.toItemCommand(order.id))

            val payment = paymentService.request(
                PaymentCommand.Request(order.id, POINT).toEntity(BigDecimal("1000")),
            )

            // when
            assertThrows<CoreException> {
                paymentProcessor.process(PaymentCommand.Process(order.id, payment.id))
            }

            // then
            val reloadedStock = productStockRepository.findById(productStock.id).get()
            val reloadedPayment = paymentRepository.findById(payment.id).get()
            val reloadedOrder = orderRepository.findById(order.id).get()

            assertThat(reloadedStock.quantity.value).isEqualTo(0)
            assertThat(reloadedPayment.status).isEqualTo(Payment.Status.FAILED)
            assertThat(reloadedOrder.status).isEqualTo(Order.Status.ORDER_FAIL)
        }

        @Test
        fun `결제가 성공하면 재고, 포인트가 차감되고 주문과 결제 상태가 SUCCESS로 변경된다`() {
            // given
            val userId = 1L

            val point = pointRepository.save(Point.create(userId, BigDecimal("10000")))

            val product = productRepository.save(
                Product.create(1L, "상품", "설명", BigDecimal("1000")),
            )

            val option = productOptionRepository.save(
                ProductOption.create(product.id, 1L, "화이트", "M", "노출용이름", BigDecimal("1000")),
            )

            val stock = productStockRepository.save(
                ProductStock.create(option.id, 10),
            )

            val orderCommand = OrderCommand.RequestOrder(
                userId,
                BigDecimal("1000"),
                BigDecimal("1000"),
                PAYMENT_REQUEST,
                listOf(Item(option.id, 1)),
            )

            val order = orderService.request(orderCommand)
            orderItemService.register(orderCommand.toItemCommand(order.id))

            val payment = paymentService.request(
                PaymentCommand.Request(order.id, POINT).toEntity(BigDecimal("2000")),
            )

            // when
            paymentProcessor.process(PaymentCommand.Process(order.id, payment.id))

            // then
            val reloadedPayment = paymentRepository.findById(payment.id).get()
            val reloadedOrder = orderRepository.findById(order.id).get()
            val reloadedPoint = pointRepository.findById(point.id).get()
            val reloadedStock = productStockRepository.findById(stock.id).get()

            assertThat(reloadedPayment.status).isEqualTo(Payment.Status.SUCCESS)
            assertThat(reloadedOrder.status).isEqualTo(Order.Status.ORDER_SUCCESS)
            assertThat(reloadedPoint.amount.value).isEqualByComparingTo(BigDecimal(10000 - 2000))
            assertThat(reloadedStock.quantity.value).isEqualTo(9)
        }
    }

    /*@DisplayName("결제 동시성")
    @Nested
    inner class Concurrency {

        @Test
        fun `동시에 여러 결제를 요청해도 재고와 포인트가 정상적으로 차감된다`() {
            // given
            val userId = 1L

            val point = pointRepository.save(Point.create(userId, BigDecimal("100000"))) // 충분한 포인트
            val product = productRepository.save(Product.create(1L, "상품", "설명", BigDecimal("1000")))
            val option = productOptionRepository.save(
                ProductOption.create(product.id, 1L, "화이트", "M", "노출용이름", BigDecimal("1000"))
            )
            val stock = productStockRepository.save(ProductStock.create(option.id, 10)) // 재고 10개

            val numberOfThreads = 5
            val latch = CountDownLatch(numberOfThreads)
            val executor = Executors.newFixedThreadPool(numberOfThreads)

            repeat(numberOfThreads) {
                executor.submit {
                    try {
                        val orderCommand = OrderCommand.RequestOrder(
                            userId,
                            BigDecimal("1000"),
                            BigDecimal("1000"),
                            PAYMENT_REQUEST,
                            listOf(Item(option.id, 1)),
                        )

                        val order = orderService.request(orderCommand)
                        orderItemService.register(orderCommand.toItemCommand(order.id))

                        val payment = paymentService.request(
                            PaymentCommand.Request(order.id, Payment.Method.POINT, BigDecimal("2000"))
                        )

                        paymentProcessor.process(PaymentCommand.Process(payment.id, order.id))
                    } catch (e: Exception) {
                        println("실패: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            // wait for all
            latch.await()

            // then
            val reloadedStock = productStockRepository.findById(stock.id).get()
            val reloadedPoint = pointRepository.findById(point.id).get()
            val successPayments = paymentRepository.findAll().filter { it.status == Payment.Status.SUCCESS }
            val failedPayments = paymentRepository.findAll().filter { it.status == Payment.Status.FAILED }

            println("성공: ${successPayments.size}, 실패: ${failedPayments.size}")

            assertThat(reloadedStock.quantity.value).isEqualTo(10 - successPayments.size)
            assertThat(reloadedPoint.amount.value).isEqualTo(100_000 - (successPayments.size * 2000))
            assertThat(successPayments.size + failedPayments.size).isEqualTo(numberOfThreads)
        }
    }*/
}
