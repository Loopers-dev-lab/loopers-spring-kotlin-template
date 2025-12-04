# Concurrency Guide

동시성 제어 가이드입니다.

---

## 락킹 전략 선택

| 상황 | 전략 | 예시 |
|------|------|------|
| 충돌 빈도 높음, 일관성 중요 | Pessimistic Lock | 포인트, 재고 |
| 충돌 빈도 낮음, 성능 중요 | Optimistic Lock | 쿠폰 사용 |

---

## Pessimistic Lock (비관적 락)

충돌이 발생할 것으로 예상하고 미리 락을 잡습니다.

### 사용 시점

- 포인트 차감/충전
- 재고 차감
- 잔액 변경

### Repository 구현

```kotlin
interface PointAccountJpaRepositoryInterface : JpaRepository<PointAccount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PointAccount p WHERE p.userId = :userId")
    fun findByUserIdWithLock(userId: Long): PointAccount?
}
```

### Service에서 사용

```kotlin
@Service
class PointService(
    private val pointAccountRepository: PointAccountRepository
) {
    @Transactional
    fun deduct(userId: Long, amount: Money): PointAccount {
        val account = pointAccountRepository.findByUserIdWithLock(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "포인트 계정을 찾을 수 없습니다")

        account.deduct(amount)
        return pointAccountRepository.save(account)
    }
}
```

---

## Optimistic Lock (낙관적 락)

충돌이 드물 것으로 예상하고, 커밋 시점에 충돌을 감지합니다.

### 사용 시점

- 쿠폰 사용
- 주문 상태 변경
- 충돌 빈도가 낮은 업데이트

### Entity에 @Version 추가

```kotlin
@Entity
@Table(name = "issued_coupons")
class IssuedCoupon(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    var status: CouponStatus = CouponStatus.AVAILABLE,

    var usedAt: LocalDateTime? = null,

    @Version
    val version: Long = 0
) {
    fun use() {
        if (status != CouponStatus.AVAILABLE) {
            throw CoreException(ErrorType.BAD_REQUEST, "사용 가능한 쿠폰이 아닙니다")
        }
        status = CouponStatus.USED
        usedAt = LocalDateTime.now()
    }
}
```

### Facade에서 @Retryable 사용

충돌 시 자동 재시도를 위해 `@Retryable`을 사용합니다.

```kotlin
@Service
class OrderFacade(
    private val orderService: OrderService,
    private val couponService: CouponService,
    private val pointService: PointService
) {
    @Retryable(
        retryFor = [ObjectOptimisticLockingFailureException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100)
    )
    @Transactional
    fun placeOrderWithCoupon(command: PlaceOrderCommand): OrderResult {
        // 쿠폰 사용 (Optimistic Lock)
        couponService.useCoupon(command.couponId)

        // 포인트 차감 (Pessimistic Lock)
        pointService.deduct(command.userId, command.usePoint)

        // 주문 생성
        return orderService.createOrder(command)
    }
}
```

**설정 필요**: `@EnableRetry` 추가

```kotlin
@EnableRetry
@Configuration
class RetryConfig
```

---

## 동시성 테스트

Application Layer에서 동시성 제어가 잘 되었는지 테스트합니다.

### 테스트 패턴

```kotlin
@SpringBootTest
class SomeFacadeConcurrencyTest @Autowired constructor(
    private val facade: SomeFacade,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동시 요청 시 정합성이 유지된다")
    @Test
    fun `concurrent requests maintain consistency`() {
        // given
        val threadCount = 10
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        // when
        repeat(threadCount) { index ->
            executorService.submit {
                try {
                    // 동시 실행할 작업
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        assertThat(successCount.get()).isEqualTo(threadCount)
    }
}
```

### 예시: 좋아요 동시성 테스트

`LikeFacadeConcurrencyTest.kt`:

```kotlin
@SpringBootTest
class LikeFacadeConcurrencyTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val productStatisticRepository: ProductStatisticRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("여러 유저가 동일한 상품에 동시에 좋아요를 추가해도, 좋아요 개수가 정확히 증가해야 한다")
    @Test
    fun `concurrent users adding likes should increase count correctly`() {
        // given
        val product = createProduct()
        val threadCount = 10
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        // when
        repeat(threadCount) { index ->
            executorService.submit {
                try {
                    val userId = index + 1L
                    likeFacade.addLike(userId, product.id)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        assertThat(successCount.get()).isEqualTo(threadCount)

        val productStatistic = productStatisticRepository.findByProductId(product.id)!!
        assertThat(productStatistic.likeCount).isEqualTo(threadCount.toLong())
    }

    @DisplayName("동일한 유저가 같은 상품에 중복으로 좋아요를 시도해도, 좋아요는 한 번만 추가되어야 한다")
    @Test
    fun `same user cannot add multiple likes to same product concurrently`() {
        // given
        val userId = 1L
        val product = createProduct()
        val threadCount = 5
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when
        repeat(threadCount) {
            executorService.submit {
                try {
                    likeFacade.addLike(userId, product.id)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount)

        val productStatistic = productStatisticRepository.findByProductId(product.id)!!
        assertThat(productStatistic.likeCount).isEqualTo(1)
    }
}
```

### 예시: 주문 동시성 테스트

`OrderFacadeConcurrencyTest.kt`:

```kotlin
@SpringBootTest
class OrderFacadeConcurrencyTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val productRepository: ProductRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일한 쿠폰으로 여러 기기에서 동시에 주문해도, 쿠폰은 단 한번만 사용되어야 한다")
    @Test
    fun `same coupon can only be used once even with concurrent orders`() {
        // given
        val userId = 1L
        val product = createProduct(price = Money.krw(10000))
        val issuedCoupon = createIssuedCoupon(userId = userId)
        createPointAccount(userId = userId)

        val threadCount = 5
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when
        repeat(threadCount) {
            executorService.submit {
                try {
                    val criteria = OrderCriteria.PlaceOrder(
                        userId = userId,
                        items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
                        usePoint = Money.krw(5000),
                        issuedCouponId = issuedCoupon.id,
                    )
                    orderFacade.placeOrder(criteria)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failureCount.get()).isEqualTo(threadCount - 1)

        val updatedIssuedCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!
        assertThat(updatedIssuedCoupon.status).isEqualTo(UsageStatus.USED)
    }

    @DisplayName("동일한 상품에 대해 여러 주문이 동시에 요청되어도, 재고가 정상적으로 차감되어야 한다")
    @Test
    fun `concurrent orders for same product should deduct stock correctly`() {
        // given
        val initialStock = 10
        val product = createProduct(stock = Stock.of(initialStock))

        val threadCount = 10
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        repeat(threadCount) { index ->
            val userId = index + 1L
            createPointAccount(userId = userId)
        }

        // when
        repeat(threadCount) { index ->
            executorService.submit {
                try {
                    val userId = index + 1L
                    val criteria = OrderCriteria.PlaceOrder(
                        userId = userId,
                        items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
                        usePoint = product.price,
                        issuedCouponId = null,
                    )
                    orderFacade.placeOrder(criteria)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        assertThat(successCount.get()).isEqualTo(initialStock)

        val updatedProduct = productRepository.findById(product.id)!!
        assertThat(updatedProduct.stock.amount).isEqualTo(0)
    }
}
```

---

## 관련 문서

| 주제 | 문서 |
|------|------|
| 아키텍처 | [architecture-guide.md](architecture-guide.md) |
| 에러 처리 | [error-handling-guide.md](error-handling-guide.md) |
