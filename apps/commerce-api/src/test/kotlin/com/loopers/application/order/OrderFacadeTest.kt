package com.loopers.application.order

import com.loopers.common.fixture.BrandFixture
import com.loopers.common.fixture.ProductFixture
import com.loopers.common.fixture.StockFixture
import com.loopers.domain.brand.BrandService
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.domain.stock.StockRepository
import com.loopers.domain.stock.StockService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class OrderFacadeTest {

    private val productService: ProductService = mockk()
    private val brandService: BrandService = mockk()
    private val stockRepository: StockRepository = mockk()
    private lateinit var stockService: StockService
    private val pointService: PointService = mockk()
    private val orderService: OrderService = mockk(relaxed = true)
    private val paymentService: PaymentService = mockk()
    private val couponService: CouponService = mockk()
    private lateinit var orderFacade: OrderFacade

    @BeforeEach
    fun setUp() {
        stockService = StockService(stockRepository)
        orderFacade = OrderFacade(
            productService = productService,
            brandService = brandService,
            stockService = stockService,
            pointService = pointService,
            orderService = orderService,
            paymentService = paymentService,
            couponService = couponService,
        )
    }

    @DisplayName("주문 생성 시")
    @Nested
    inner class CreateOrder {

        @DisplayName("존재하지 않는 상품으로 주문 시, NOT_FOUND 에러가 발생한다.")
        @Test
        fun throwsException_whenProductDoesNotExist() {
            // arrange
            val userId = 1L
            val productId = 999L

            val orderItems = listOf(
                OrderItemCommand(productId = productId, quantity = 1),
            )

            every { productService.getProductById(listOf(productId)) } returns emptyMap()
            every { brandService.getBrandById(emptyList()) } returns emptyMap()

            // act & assert
            val exception = assertThrows<CoreException> {
                orderFacade.createOrder(userId, orderItems)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("존재하지 않는 상품입니다")
            assertThat(exception.message).contains(productId.toString())
        }

        @DisplayName("재고가 부족한 경우, OUT_OF_STOCK 에러가 발생한다.")
        @Test
        fun throwsException_whenStockIsInsufficient() {
            // arrange
            val userId = 1L
            val brandId = 10L
            val productId = 100L
            val currentStock = 50
            val requestQuantity = 100

            val product = ProductFixture.create(id = productId, brandId = brandId)
            val brand = BrandFixture.create(id = brandId)
            val stock = StockFixture.create(
                productId = productId,
                quantity = currentStock,
            )

            val orderItems = listOf(
                OrderItemCommand(productId = productId, quantity = requestQuantity),
            )

            every { productService.getProductById(listOf(productId)) } returns mapOf(productId to product)
            every { brandService.getBrandById(listOf(brandId)) } returns mapOf(brandId to brand)
            every { stockRepository.findByProductIdWithLock(productId) } returns stock

            // act & assert
            val exception = assertThrows<CoreException> {
                orderFacade.createOrder(userId, orderItems)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.OUT_OF_STOCK)
            assertThat(exception.message).contains("재고가 부족합니다")
        }

        @DisplayName("포인트가 부족한 경우, INSUFFICIENT_POINT 에러가 발생한다.")
        @Test
        fun throwsException_whenPointIsInsufficient() {
            // arrange
            val userId = 1L
            val brandId = 10L
            val productId = 100L
            val requestQuantity = 1

            val product = ProductFixture.create(id = productId, brandId = brandId)
            val brand = BrandFixture.create(id = brandId)
            val stock = StockFixture.create(productId = productId, quantity = 100)

            val orderItems = listOf(
                OrderItemCommand(productId = productId, quantity = requestQuantity),
            )

            val totalAmount = BigDecimal("10000.00")

            every { productService.getProductById(listOf(productId)) } returns mapOf(productId to product)
            every { brandService.getBrandById(listOf(brandId)) } returns mapOf(brandId to brand)
            every { stockRepository.findByProductIdWithLock(productId) } returns stock
            every { pointService.deductPoint(userId, totalAmount) } throws CoreException(
                ErrorType.INSUFFICIENT_POINT,
                "포인트가 부족합니다.",
            )

            // act & assert
            val exception = assertThrows<CoreException> {
                orderFacade.createOrder(userId, orderItems)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.INSUFFICIENT_POINT)
            assertThat(exception.message).contains("포인트가 부족합니다")
        }
    }
}
