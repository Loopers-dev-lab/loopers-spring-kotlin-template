package com.loopers.application.payment

import com.loopers.domain.order.OrderItemService
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.dto.command.OrderCommand
import com.loopers.domain.order.dto.command.OrderItemCommand
import com.loopers.domain.order.entity.Order
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.dto.command.PaymentCommand
import com.loopers.domain.payment.entity.Payment
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
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

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

            val point = pointRepository.save(Point.Companion.create(userId, BigDecimal("10000")))

            val product = productRepository.save(
                Product.Companion.create(1L, "상품", "설명", BigDecimal("1000")),
            )

            val option = productOptionRepository.save(
                ProductOption.Companion.create(product.id, 1L, "화이트", "M", "노출용이름", BigDecimal("1000")),
            )

            val productStock = productStockRepository.save(
                ProductStock.Companion.create(option.id, 10),
            )

            val orderCommand = OrderCommand.RequestOrder(
                userId,
                BigDecimal("1000"),
                BigDecimal("1000"),
                Order.Status.PAYMENT_REQUEST,
                listOf(OrderItemCommand.Register.Item(option.id, 1)),
            )

            val order = orderService.request(orderCommand)
            orderItemService.register(orderCommand.toItemCommand(order.id))

            val payment = paymentService.request(
                PaymentCommand.Request(order.id, Payment.Method.POINT).toEntity(BigDecimal("1000")),
            )

            // when
            paymentProcessor.process(PaymentCommand.Process(order.id, payment.id))

            // then
            val updatedPayment = paymentService.get(payment.id)

            Assertions.assertThat(updatedPayment.status).isEqualTo(Payment.Status.SUCCESS)
        }

        @Test
        fun `포인트가 부족하면 POINT_NOT_ENOUGH 예외가 발생한다`() {
            // given
            val userId = 1L
            pointRepository.save(Point.Companion.create(userId, BigDecimal("500")))

            val product = productRepository.save(
                Product.Companion.create(1L, "상품", "설명", BigDecimal("1000")),
            )

            val option = productOptionRepository.save(
                ProductOption.Companion.create(product.id, 1L, "화이트", "M", "노출용이름", BigDecimal("1000")),
            )

            productStockRepository.save(ProductStock.Companion.create(option.id, 10))

            val orderCommand = OrderCommand.RequestOrder(
                userId,
                BigDecimal("1000"),
                BigDecimal("1000"),
                Order.Status.PAYMENT_REQUEST,
                listOf(OrderItemCommand.Register.Item(option.id, 1)),
            )
            val order = orderService.request(orderCommand)
            orderItemService.register(orderCommand.toItemCommand(order.id))

            val payment = paymentService.request(
                PaymentCommand.Request(order.id, Payment.Method.POINT).toEntity(BigDecimal("1000")),
            )

            // expect
            val exception = assertThrows<CoreException> {
                paymentProcessor.process(PaymentCommand.Process(order.id, payment.id))
            }

            Assertions.assertThat(exception.errorType).isEqualTo(ErrorType.POINT_NOT_ENOUGH)
        }

        @Test
        fun `재고가 부족하면 STOCK_NOT_ENOUGH 예외가 발생한다`() {
            // given
            val userId = 1L
            pointRepository.save(Point.Companion.create(userId, BigDecimal("10000")))

            val product = productRepository.save(
                Product.Companion.create(1L, "상품", "설명", BigDecimal("1000")),
            )

            val option = productOptionRepository.save(
                ProductOption.Companion.create(product.id, 1L, "화이트", "M", "노출용이름", BigDecimal("1000")),
            )

            productStockRepository.save(ProductStock.Companion.create(option.id, 0))

            val orderCommand = OrderCommand.RequestOrder(
                userId,
                BigDecimal("1000"),
                BigDecimal("1000"),
                Order.Status.PAYMENT_REQUEST,
                listOf(OrderItemCommand.Register.Item(option.id, 1)),
            )
            val order = orderService.request(orderCommand)
            orderItemService.register(orderCommand.toItemCommand(order.id))

            val payment = paymentService.request(
                PaymentCommand.Request(order.id, Payment.Method.POINT).toEntity(BigDecimal("1000")),
            )

            // expect
            val exception = assertThrows<CoreException> {
                paymentProcessor.process(PaymentCommand.Process(order.id, payment.id))
            }

            Assertions.assertThat(exception.errorType).isEqualTo(ErrorType.PRODUCT_STOCK_NOT_ENOUGH)
        }

        @Test
        fun `포인트가 부족하면 결제와 주문은 실패 상태가 되고, 포인트는 차감되지 않는다`() {
            // given
            val userId = 1L

            val point = pointRepository.save(Point.Companion.create(userId, BigDecimal("100")))

            val product = productRepository.save(
                Product.Companion.create(1L, "상품", "설명", BigDecimal("1000")),
            )

            val option = productOptionRepository.save(
                ProductOption.Companion.create(product.id, 1L, "화이트", "M", "노출용이름", BigDecimal("1000")),
            )

            val productStock = productStockRepository.save(ProductStock.Companion.create(option.id, 10))

            val orderCommand = OrderCommand.RequestOrder(
                userId,
                BigDecimal("1000"),
                BigDecimal("1000"),
                Order.Status.PAYMENT_REQUEST,
                listOf(OrderItemCommand.Register.Item(option.id, 1)),
            )

            val order = orderService.request(orderCommand)
            orderItemService.register(orderCommand.toItemCommand(order.id))

            val payment = paymentService.request(
                PaymentCommand.Request(order.id, Payment.Method.POINT).toEntity(BigDecimal("1000")),
            )

            // when
            assertThrows<CoreException> {
                paymentProcessor.process(PaymentCommand.Process(order.id, payment.id))
            }

            // then
            val reloadedPoint = pointRepository.findById(point.id).get()
            val reloadedPayment = paymentRepository.findById(payment.id).get()
            val reloadedOrder = orderRepository.findById(order.id).get()

            Assertions.assertThat(reloadedPoint.amount.value).isEqualByComparingTo(BigDecimal(100))
            Assertions.assertThat(reloadedPayment.status).isEqualTo(Payment.Status.FAILED)
            Assertions.assertThat(reloadedOrder.status).isEqualTo(Order.Status.ORDER_FAIL)
        }

        @Test
        fun `재고가 부족하면 결제와 주문은 실패 상태가 되고, 재고는 차감되지 않는다`() {
            // given
            val userId = 1L

            val point = pointRepository.save(Point.Companion.create(userId, BigDecimal("2000")))

            val product = productRepository.save(
                Product.Companion.create(1L, "상품", "설명", BigDecimal("1000")),
            )

            val option = productOptionRepository.save(
                ProductOption.Companion.create(product.id, 1L, "화이트", "M", "노출용이름", BigDecimal("1000")),
            )

            val productStock = productStockRepository.save(ProductStock.Companion.create(option.id, 0))

            val orderCommand = OrderCommand.RequestOrder(
                userId,
                BigDecimal("1000"),
                BigDecimal("1000"),
                Order.Status.PAYMENT_REQUEST,
                listOf(OrderItemCommand.Register.Item(option.id, 1)),
            )

            val order = orderService.request(orderCommand)
            orderItemService.register(orderCommand.toItemCommand(order.id))

            val payment = paymentService.request(
                PaymentCommand.Request(order.id, Payment.Method.POINT).toEntity(BigDecimal("1000")),
            )

            // when
            assertThrows<CoreException> {
                paymentProcessor.process(PaymentCommand.Process(order.id, payment.id))
            }

            // then
            val reloadedStock = productStockRepository.findById(productStock.id).get()
            val reloadedPayment = paymentRepository.findById(payment.id).get()
            val reloadedOrder = orderRepository.findById(order.id).get()

            Assertions.assertThat(reloadedStock.quantity.value).isEqualTo(0)
            Assertions.assertThat(reloadedPayment.status).isEqualTo(Payment.Status.FAILED)
            Assertions.assertThat(reloadedOrder.status).isEqualTo(Order.Status.ORDER_FAIL)
        }

        @Test
        fun `결제가 성공하면 재고, 포인트가 차감되고 주문과 결제 상태가 SUCCESS로 변경된다`() {
            // given
            val userId = 1L

            val point = pointRepository.save(Point.Companion.create(userId, BigDecimal("10000")))

            val product = productRepository.save(
                Product.Companion.create(1L, "상품", "설명", BigDecimal("1000")),
            )

            val option = productOptionRepository.save(
                ProductOption.Companion.create(product.id, 1L, "화이트", "M", "노출용이름", BigDecimal("1000")),
            )

            val stock = productStockRepository.save(
                ProductStock.Companion.create(option.id, 10),
            )

            val orderCommand = OrderCommand.RequestOrder(
                userId,
                BigDecimal("1000"),
                BigDecimal("1000"),
                Order.Status.PAYMENT_REQUEST,
                listOf(OrderItemCommand.Register.Item(option.id, 1)),
            )

            val order = orderService.request(orderCommand)
            orderItemService.register(orderCommand.toItemCommand(order.id))

            val payment = paymentService.request(
                PaymentCommand.Request(order.id, Payment.Method.POINT).toEntity(BigDecimal("2000")),
            )

            // when
            paymentProcessor.process(PaymentCommand.Process(order.id, payment.id))

            // then
            val reloadedPayment = paymentRepository.findById(payment.id).get()
            val reloadedOrder = orderRepository.findById(order.id).get()
            val reloadedPoint = pointRepository.findById(point.id).get()
            val reloadedStock = productStockRepository.findById(stock.id).get()

            Assertions.assertThat(reloadedPayment.status).isEqualTo(Payment.Status.SUCCESS)
            Assertions.assertThat(reloadedOrder.status).isEqualTo(Order.Status.ORDER_SUCCESS)
            Assertions.assertThat(reloadedPoint.amount.value).isEqualByComparingTo(BigDecimal(10000 - 2000))
            Assertions.assertThat(reloadedStock.quantity.value).isEqualTo(9)
        }
    }

    @DisplayName("결제 동시성")
    @Nested
    inner class Concurrency {

        @Test
        fun `동일한 유저가 하나의 결제를 동시에 요청해도 재고와 포인트가 한번만 처리되어야 한다`() {
            // given
            val userId = 1L
            val chargePoint = 10000
            val productPrice = 1000
            val productOptionPrice = 1000
            val totalPrice = productPrice + productOptionPrice
            val productStock = 10

            val point = pointRepository.save(Point.Companion.create(userId, BigDecimal(chargePoint)))
            val product = productRepository.save(Product.Companion.create(1L, "상품", "설명", BigDecimal(productPrice)))
            val option = productOptionRepository.save(
                ProductOption.Companion.create(product.id, 1L, "BLACK", "M", "노출용이름", BigDecimal(productOptionPrice)),
            )
            val stock = productStockRepository.save(ProductStock.Companion.create(option.id, productStock))

            val threadCount = 5
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)

            val orderCommand = OrderCommand.RequestOrder(
                userId,
                BigDecimal(totalPrice),
                BigDecimal(totalPrice),
                Order.Status.PAYMENT_REQUEST,
                listOf(OrderItemCommand.Register.Item(option.id, 1)),
            )

            val order = orderService.request(orderCommand)
            orderItemService.register(orderCommand.toItemCommand(order.id))

            val payment = paymentService.request(
                PaymentCommand.Request(order.id, Payment.Method.POINT).toEntity(BigDecimal(totalPrice)),
            )

            var failCount = 0
            repeat(threadCount) {
                executor.submit {
                    try {
                        paymentProcessor.process(PaymentCommand.Process(payment.id, order.id))
                    } catch (e: Exception) {
                        failCount++
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()

            // then
            val reloadedStock = productStockRepository.findById(stock.id).get()
            val reloadedPoint = pointRepository.findById(point.id).get()

            Assertions.assertThat(reloadedStock.quantity.value).isEqualTo(productStock - 1)
            Assertions.assertThat(reloadedPoint.amount.value).isEqualByComparingTo(BigDecimal(chargePoint - totalPrice))
            Assertions.assertThat(failCount).isEqualTo(threadCount - 1)
        }

        @Test
        fun `동일한 유저가 서로 다른 주문을 동시에 수행해도 포인트가 정상적으로 차감되어야 한다`() {
            // given
            val userId = 1L
            val chargePoint = 100000
            val productPrice = 1000
            val productOptionPrice = 1000
            val totalPrice = productPrice + productOptionPrice
            val orderCount = 10

            pointRepository.save(Point.Companion.create(userId, BigDecimal(chargePoint)))
            val product = productRepository.save(Product.Companion.create(1L, "상품", "설명", BigDecimal(productPrice)))
            val option = productOptionRepository.save(
                ProductOption.Companion.create(
                    product.id,
                    1L,
                    "BLACK",
                    "M",
                    "옵션",
                    BigDecimal(productOptionPrice),
                ),
            )
            productStockRepository.save(ProductStock.Companion.create(option.id, orderCount)) // 재고는 충분하게

            val latch = CountDownLatch(orderCount)
            val executor = Executors.newFixedThreadPool(orderCount)

            repeat(orderCount) {
                executor.submit {
                    try {
                        val orderCommand = OrderCommand.RequestOrder(
                            userId,
                            BigDecimal(totalPrice),
                            BigDecimal(totalPrice),
                            Order.Status.PAYMENT_REQUEST,
                            listOf(OrderItemCommand.Register.Item(option.id, 1)),
                        )

                        val order = orderService.request(orderCommand)
                        orderItemService.register(orderCommand.toItemCommand(order.id))

                        val payment = paymentService.request(
                            PaymentCommand.Request(order.id, Payment.Method.POINT).toEntity(BigDecimal(totalPrice)),
                        )

                        paymentProcessor.process(PaymentCommand.Process(payment.id, order.id))
                    } catch (e: Exception) {
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()

            // then
            val point = pointRepository.findByUserId(userId)!!
            val successPaymentCount = paymentRepository.findAll().count { it.status == Payment.Status.SUCCESS }
            Assertions.assertThat(point.amount.value)
                .isEqualByComparingTo(BigDecimal(chargePoint - (successPaymentCount * totalPrice)))
        }

        @Test
        fun `동일한 상품에 대해 여러 주문이 동시에 요청되어도 재고가 정상적으로 차감되어야 한다`() {
            // given
            val userId = 1L
            val chargePoint = 100000
            val productPrice = 1000
            val productOptionPrice = 1000
            val totalPrice = productPrice + productOptionPrice
            val productStock = 3
            val orderCount = 10

            pointRepository.save(Point.Companion.create(userId, BigDecimal(chargePoint)))
            val product = productRepository.save(Product.Companion.create(1L, "상품", "설명", BigDecimal(productPrice)))
            val option = productOptionRepository.save(
                ProductOption.Companion.create(
                    product.id,
                    1L,
                    "BLACK",
                    "M",
                    "옵션",
                    BigDecimal(productOptionPrice),
                ),
            )
            val stock = productStockRepository.save(ProductStock.Companion.create(option.id, productStock))

            val latch = CountDownLatch(orderCount)
            val executor = Executors.newFixedThreadPool(orderCount)

            repeat(orderCount) {
                executor.submit {
                    try {
                        val orderCommand = OrderCommand.RequestOrder(
                            userId,
                            BigDecimal(totalPrice),
                            BigDecimal(totalPrice),
                            Order.Status.PAYMENT_REQUEST,
                            listOf(OrderItemCommand.Register.Item(option.id, 1)),
                        )

                        val order = orderService.request(orderCommand)
                        orderItemService.register(orderCommand.toItemCommand(order.id))

                        val payment = paymentService.request(
                            PaymentCommand.Request(order.id, Payment.Method.POINT).toEntity(BigDecimal(totalPrice)),
                        )

                        paymentProcessor.process(PaymentCommand.Process(payment.id, order.id))
                    } catch (e: Exception) {
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()

            // then
            val reloadedStock = productStockRepository.findById(stock.id).get()
            val successPaymentCount = paymentRepository.findAll().count { it.status == Payment.Status.SUCCESS }
            Assertions.assertThat(reloadedStock.quantity.value).isEqualTo(productStock - successPaymentCount)
        }
    }
}
