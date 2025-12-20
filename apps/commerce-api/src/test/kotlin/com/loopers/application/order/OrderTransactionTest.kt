package com.loopers.application.order

import com.loopers.IntegrationTestSupport
import com.loopers.domain.common.vo.Money
import com.loopers.domain.payment.PaymentClient
import com.loopers.domain.payment.dto.PaymentDto
import com.loopers.domain.point.PointModel
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.stock.StockModel
import com.loopers.domain.user.UserFixture
import com.loopers.infrastructure.point.PointJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.product.stock.StockJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.doReturn
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

class OrderTransactionTest(
    private val databaseCleanUp: DatabaseCleanUp,
    private val userRepository: UserJpaRepository,
    private val pointRepository: PointJpaRepository,
    private val productRepository: ProductJpaRepository,
    private val stockRepository: StockJpaRepository,
    private val orderFacade: OrderFacade,
    private val dataSource: DataSource,
    @MockitoBean private val paymentClient: PaymentClient,
) : IntegrationTestSupport() {

    @BeforeEach
    fun setup() {
        // PaymentClient mock 설정 - 항상 성공 반환
        doReturn(
            ApiResponse.success(
                PaymentDto.Response(
                    transactionKey = UUID.randomUUID().toString(),
                    status = "SUCCESS",
                ),
            ),
        )
            .`when`(paymentClient)
            .requestPayment(anyKotlin())
    }

    // Kotlin에서 Mockito any() 사용을 위한 helper 함수
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyKotlin(): T {
        Mockito.any<T>()
        return null as T
    }

    @AfterEach
    fun teardown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("비동기 방식 성능 테스트 - 커넥션 풀 압박 상황")
    @Test
    fun asyncOrderPerformance_underConnectionPoolPressure() {
        // ============ 테스트 설정 ============
        val orderCount = 20
        val occupiedConnectionCount = 7 // 10개 중 7개 점유 → 3개만 사용 가능

        // ============ 테스트 데이터 준비 ============
        val users = prepareUsers(orderCount)
        val products = prepareProducts(orderCount)

        // ============ 커넥션 점유 시작 ============
        val occupiedConnections = occupyConnections(occupiedConnectionCount)

        println("=".repeat(60))
        println("📊 비동기 방식 테스트 환경")
        println("=".repeat(60))
        println("총 커넥션 풀 크기: 10")
        println("점유된 커넥션 수: $occupiedConnectionCount")
        println("사용 가능한 커넥션: ${10 - occupiedConnectionCount}")
        println("동시 주문 요청 수: $orderCount")
        println("=".repeat(60))

        val executor = Executors.newFixedThreadPool(orderCount)

        try {
            val asyncTime = measureTimeMillis {
                val futures =
                    (0 until orderCount).map { i ->
                        CompletableFuture.supplyAsync(
                            {
                                val command =
                                    OrderCommand(
                                        orderItems =
                                            listOf(
                                                OrderItemCommand(
                                                    products[
                                                        i,
                                                    ]
                                                        .id,
                                                    1L,
                                                    BigDecimal
                                                        .valueOf(
                                                            1000L,
                                                        ),
                                                ),
                                            ),
                                        cardType = "CREDIT",
                                        cardNo =
                                            "1234-5678-9012-3456",
                                        couponId = null,
                                    )
                                orderFacade.order(
                                    users[i].id,
                                    command,
                                ) // 비동기 (이벤트 발행)
                            },
                            executor,
                        )
                    }
                futures.forEach { it.join() }
            }

            // 이벤트 처리 완료 대기 (비동기이므로)
            Thread.sleep(2000)

            println()
            println("=".repeat(60))
            println("📈 비동기 방식 결과")
            println("=".repeat(60))
            println("소요 시간: ${asyncTime}ms")
            println("주문당 평균: ${asyncTime / orderCount}ms")
            println("=".repeat(60))
        } finally {
            releaseConnections(occupiedConnections)
            executor.shutdown()
        }
    }

    @DisplayName("동기 방식 성능 테스트 - 커넥션 풀 압박 상황")
    @Test
    fun syncOrderPerformance_underConnectionPoolPressure() {
        // ============ 테스트 설정 ============
        val orderCount = 20
        val occupiedConnectionCount = 7 // 10개 중 7개 점유 → 3개만 사용 가능

        // ============ 테스트 데이터 준비 ============
        val users = prepareUsers(orderCount)
        val products = prepareProducts(orderCount)

        // ============ 커넥션 점유 시작 ============
        val occupiedConnections = occupyConnections(occupiedConnectionCount)

        println("=".repeat(60))
        println("📊 동기 방식 테스트 환경")
        println("=".repeat(60))
        println("총 커넥션 풀 크기: 10")
        println("점유된 커넥션 수: $occupiedConnectionCount")
        println("사용 가능한 커넥션: ${10 - occupiedConnectionCount}")
        println("동시 주문 요청 수: $orderCount")
        println("=".repeat(60))

        val executor = Executors.newFixedThreadPool(orderCount)

        try {
            val syncTime = measureTimeMillis {
                val futures =
                    (0 until orderCount).map { i ->
                        CompletableFuture.supplyAsync(
                            {
                                val command =
                                    OrderCommand(
                                        orderItems =
                                            listOf(
                                                OrderItemCommand(
                                                    products[
                                                        i,
                                                    ]
                                                        .id,
                                                    1L,
                                                    BigDecimal
                                                        .valueOf(
                                                            1000L,
                                                        ),
                                                ),
                                            ),
                                        cardType = "CREDIT",
                                        cardNo =
                                            "1234-5678-9012-3456",
                                        couponId = null,
                                    )
                                orderFacade.orderSync(
                                    users[i].id,
                                    command,
                                ) // 동기 (직접 처리)
                            },
                            executor,
                        )
                    }
                futures.forEach { it.join() }
            }

            println()
            println("=".repeat(60))
            println("📈 동기 방식 결과")
            println("=".repeat(60))
            println("소요 시간: ${syncTime}ms")
            println("주문당 평균: ${syncTime / orderCount}ms")
            println("=".repeat(60))
        } finally {
            releaseConnections(occupiedConnections)
            executor.shutdown()
        }
    }

    private fun occupyConnections(count: Int): MutableList<Connection> {
        val connections = mutableListOf<Connection>()
        repeat(count) {
            val conn = dataSource.connection
            conn.autoCommit = false // 트랜잭션 시작 → 커넥션 점유 유지
            connections.add(conn)
        }
        return connections
    }

    private fun releaseConnections(connections: List<Connection>) {
        connections.forEach { conn ->
            try {
                conn.rollback()
                conn.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun prepareUsers(count: Int) =
        (1..count).map { i ->
            val user = UserFixture.create(loginId = "t${System.nanoTime() % 10000}$i")
            userRepository.save(user)

            val point = PointModel(user.id, Money(BigDecimal.valueOf(100000L)))
            pointRepository.save(point)

            user
        }

    private fun prepareProducts(count: Int) =
        (1..count).map { i ->
            val product =
                ProductModel.create(
                    "상품_${System.nanoTime()}_$i",
                    Money(BigDecimal.valueOf(1000L)),
                    i.toLong(),
                )
            productRepository.save(product)

            val stock = StockModel.create(product.id, 1000L)
            stockRepository.save(stock)

            product
        }
}
