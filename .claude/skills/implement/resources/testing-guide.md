# Testing Guide

테스트 작성 가이드입니다.

---

## 3-Level Testing

| Level       | Purpose                | Pattern               | Location           |
|-------------|------------------------|-----------------------|--------------------|
| Unit        | 도메인 로직                 | `*Test.kt`            | `domain/<domain>/` |
| Integration | Service + Dependencies | `*IntegrationTest.kt` | `domain/<domain>/` |
| E2E         | Full API flow          | `*ApiTest.kt`         | `interfaces/api/`  |

---

## Unit Tests

도메인 로직을 격리된 환경에서 검증합니다.

**Focus:**

- 비즈니스 규칙
- Validation
- Edge cases
- 상태 전이

**Mock 대상:**

- Infrastructure와 외부 의존성만 Mock
- 가능하면 실제 도메인 구현체 사용

```kotlin
@DisplayName("PointAccount 테스트")
class PointAccountTest {

    @Nested
    @DisplayName("포인트 차감")
    inner class `deduct` {

        @Test
        @DisplayName("잔액이 충분하면 차감이 성공한다")
        fun `succeeds when balance is sufficient`() {
            val account = PointAccount(balance = Money(1000))
            account.deduct(Money(500))
            assertThat(account.balance).isEqualTo(Money(500))
        }

        @Test
        @DisplayName("잔액이 부족하면 예외가 발생한다")
        fun `throws exception when balance is insufficient`() {
            val account = PointAccount(balance = Money(100))
            assertThatThrownBy { account.deduct(Money(500)) }
                .isInstanceOf(CoreException::class.java)
        }
    }
}
```

---

## Integration Tests

여러 컴포넌트 간 상호작용을 검증합니다.

**Focus:**

- 오케스트레이션
- 트랜잭션 동작 (commit, rollback)
- DB 연동

**설정:**

- Testcontainers로 PostgreSQL 사용
- `@AfterEach`에서 `DatabaseCleanUp.truncateAllTables()`로 격리

```kotlin
@SpringBootTest
@DisplayName("OrderFacade 통합 테스트")
class OrderFacadeIntegrationTest {

    @Autowired
    private lateinit var orderFacade: OrderFacade

    @Autowired
    private lateinit var databaseCleanUp: DatabaseCleanUp

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("주문 생성")
    inner class `placeOrder` {

        @Test
        @DisplayName("재고 차감, 포인트 사용, 주문 생성이 원자적으로 처리된다")
        fun `order creation is atomic`() {
            // given
            val command = PlaceOrderCommand(...)

            // when
            val result = orderFacade.placeOrder(command)

            // then
            assertThat(result.orderId).isNotNull()
        }
    }
}
```

---

## E2E Tests

전체 HTTP 요청-응답 흐름을 검증합니다.

**Focus:**

- API 계약 (request/response 포맷)
- 인증/인가
- Middleware chain (filters, interceptors)

**설정:**

- `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- `TestRestTemplate` 사용
- 1-2개 핵심 Happy path

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Order API 테스트")
class OrderApiTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var databaseCleanUp: DatabaseCleanUp

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("POST /api/orders")
    inner class `createOrder` {

        @Test
        @DisplayName("주문 생성 성공 시 201을 반환한다")
        fun `returns 201 on success`() {
            // given
            val request = CreateOrderRequest(productId = 1, quantity = 2)

            // when
            val response = restTemplate.postForEntity(
                "/api/orders",
                request,
                OrderResponse::class.java
            )

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        }
    }
}
```

---

## BDD Style (필수)

JJUnit5 nested classes로 행위(메서드) 단위 그룹을 표현합니다.

### 네이밍 규칙

- Class: `@DisplayName("한글")` + `` inner class `english` ``
- Method: `@DisplayName("한글")` + `` fun `english`() ``

### 구조

```kotlin
@DisplayName("OrderService 테스트")
class OrderServiceTest {

    @Nested
    @DisplayName("주문 생성")
    inner class `createOrder` {

        @Test
        @DisplayName("재고가 충분하면 주문이 성공한다")
        fun `succeeds when stock is sufficient`() {
            // ...
        }

        @Test
        @DisplayName("재고가 부족하면 예외가 발생한다")
        fun `throws exception when stock is insufficient`() {
            // ...
        }
    }
}
```

**규칙:**

- Nested는 행위(메서드) 단위로만 사용
- 조건(Given)은 테스트 이름에 포함
- Nested 2단 이상 금지

---

## Single Assertion 원칙

하나의 테스트는 하나의 논리적 결과만 검증합니다.

## Test Data Setup

### Private Factory Functions

테스트 클래스마다 default 값이 있는 private factory 함수를 준비합니다. 객체 생성(create)과 저장(save)을 분리합니다.

```kotlin
@SpringBootTest
class OrderFacadeIntegrationTest {

    // Create: 객체 생성만 (DB 저장 X)
    private fun createProduct(
        price: Money = Money.krw(10000),
        stock: Stock = Stock.of(100),
        brand: Brand = createBrand(),
    ): Product {
        return Product.create(
            name = "테스트 상품",
            price = price,
            stock = stock,
            brand = brand,
        )
    }

    // Save: DB 저장
    private fun saveProduct(product: Product): Product {
        return productRepository.save(product)
    }

    // 편의를 위한 조합 함수
    private fun createAndSaveProduct(
        price: Money = Money.krw(10000),
        stock: Stock = Stock.of(100),
    ): Product {
        val brand = createAndSaveBrand()
        return saveProduct(createProduct(price = price, stock = stock, brand = brand))
    }
}
```

### Expose Only Relevant Values

테스트에서는 검증 대상 값만 노출합니다.

```kotlin
// Good - 재고 검증 테스트에서 stock만 명시
val product = createAndSaveProduct(stock = Stock.of(100))

// Good - 금액 검증 테스트에서 price만 명시
val product = createAndSaveProduct(price = Money.krw(10000))

// Bad - 불필요한 값까지 모두 명시
val product = createAndSaveProduct(
    name = "테스트 상품",
    price = Money.krw(10000),
    stock = Stock.of(100),
    brand = brand,
)
```

### Meaningful Variable Names

매직넘버 대신 의미를 설명하는 변수를 사용합니다.

```kotlin
// Bad - 98이 어디서 나온 건지 알 수 없음
val product = createAndSaveProduct(stock = Stock.of(100))
orderFacade.placeOrder(criteria)
assertThat(updatedProduct.stock.amount).isEqualTo(98)

// Good - 계산 과정이 명확함
val initialStock = 100
val orderQuantity = 2
val expectedRemainingStock = initialStock - orderQuantity

val product = createAndSaveProduct(stock = Stock.of(initialStock))
orderFacade.placeOrder(createOrderCriteria(quantity = orderQuantity))
assertThat(updatedProduct.stock.amount).isEqualTo(expectedRemainingStock)
```

### "하나의 논리적 결과"란?

**Case 1: 상태 변경 검증**

하나의 행위로 인해 변경된 상태를 검증합니다.

```kotlin
@Test
@DisplayName("차감 후 잔액이 올바르게 감소한다")
fun `balance decreases correctly`() {
    val account = PointAccount(balance = Money(1000))

    account.deduct(Money(300))

    assertThat(account.balance).isEqualTo(Money(700))
}
```

**Case 2: 예외 발생 검증**

특정 조건에서 예외가 발생하는지 검증합니다.

```kotlin
@Test
@DisplayName("CoreException이 발생한다")
fun `throws CoreException`() {
    val account = PointAccount(balance = Money(100))

    assertThatThrownBy { account.deduct(Money(500)) }
        .isInstanceOf(CoreException::class.java)
        .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
}
```

**Case 3: 반환값 검증**

메서드의 반환값이 올바른지 검증합니다.

```kotlin
@Test
@DisplayName("10% 할인 적용 후 최종 금액을 반환한다")
fun `returns final amount after 10 percent discount`() {
    val result = couponService.applyDiscount(originalPrice = 10000, discountRate = 10)

    assertThat(result).isEqualTo(9000)
}
```

**Case 4: 부수효과 검증**

행위로 인한 부수효과(저장, 이벤트 발행 등)를 검증합니다.

```kotlin
@Test
@DisplayName("주문이 DB에 저장된다")
fun `order is saved to database`() {
    val order = orderService.create(command)

    val saved = orderRepository.findById(order.id)
    assertThat(saved?.id).isEqualTo(order.id)
}
```

**Case 5: 여러 도메인 협력 결과 검증**

Facade에서 여러 도메인이 협력한 최종 결과를 검증합니다.

```kotlin
@Test
@DisplayName("재고가 차감되고 포인트가 사용된다")
fun `stock decreases and points are used`() {
    // given
    setupProduct(stock = 10)
    setupUserPoints(points = 1000)

    // when
    orderFacade.placeOrder(command)

    // then
    assertAll(
        { assertThat(getProductStock()).isEqualTo(9) },
        { assertThat(getUserPoints()).isEqualTo(500) },
    )
}
```

---

## 관련 문서

| 주제     | 문서                                                 |
|--------|----------------------------------------------------|
| 에러 처리  | [error-handling-guide.md](error-handling-guide.md) |
| 동시성 제어 | [concurrency-guide.md](concurrency-guide.md)       |
