# 클래스 다이어그램

---
# 설계 특징 & 요약

### 1. 단방향 참조 구조
- **컬렉션 참조 제거**: 컬렉션으로 표현하는 매핑을 사용하지 않음

### 2. 객체 참조 최소화
- `Product.brandId` (Long) - Brand 객체 대신 ID 참조
- `Like.userId`, `Like.productId` (Long) - 객체 대신 ID만 보유
- `Order.userId` (Long) - User 객체 대신 ID 참조
- `OrderItem.orderId`, `OrderItem.productId` (Long) - 객체 대신 ID 참조

### 3. 컬렉션 제거
- `Brand`는 `List<Product>` 보유하지 않음
- `Order`는 `List<OrderItem>` 보유하지 않음
- 필요 시 Repository를 통해 조회 (예: `findByBrandId`, `findByOrderId`)

### 4. 스냅샷 패턴
- `OrderItem`은 주문 시점의 상품 정보를 불변으로 관리
- `productId`는 데이터 참고용이며 FK 용도가 아님
- 상품 삭제/수정과 무관하게 주문 내역 보존

## 전체 도메인 모델

```mermaid
classDiagram
    class User {
        +Long id
        +String username
        +String password
        +String email
        +String name
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
        --
        +User(username, password, email, name)
    }

    class Point {
        +Long id
        +Long userId
        +BigDecimal balance
        +LocalDateTime updatedAt
        --
        +Point(userId, balance)
        +charge(amount) void
        +deduct(amount) void
        +validateBalance(amount) void
        +isBalanceSufficient(amount) boolean
    }

    class PointTransaction {
        +Long id
        +Long userId
        +TransactionType transactionType
        +BigDecimal amount
        +BigDecimal balanceBefore
        +BigDecimal balanceAfter
        +String description
        +Long orderId
        +LocalDateTime createdAt
        --
        +PointTransaction(userId, type, amount, balanceBefore, balanceAfter, description, orderId)
    }

    class TransactionType {
        <<enumeration>>
        CHARGE
        USE
        REFUND
        CANCEL
    }

    class Brand {
        +Long id
        +String name
        +String description
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
        --
        +Brand(name, description)
    }

    class Product {
        +Long id
        +String name
        +String description
        +BigDecimal price
        +Long brandId
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
        --
        +Product(name, description, price, brandId)
    }

    class Stock {
        +Long id
        +Long productId
        +Integer quantity
        +LocalDateTime updatedAt
        --
        +Stock(productId, quantity)
        +increase(amount) void
        +deduct(amount) void
        +validateQuantity(amount) void
        +isOutOfStock() boolean
    }

    class Like {
        +Long id
        +Long userId
        +Long productId
        +LocalDateTime createdAt
        --
        +Like(userId, productId)
    }

    class Order {
        +Long id
        +Long userId
        +OrderStatus status
        +BigDecimal totalAmount
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
        --
        +Order(userId, totalAmount)
        +cancel() void
        +isOwner(userId) boolean
    }

    class OrderItem {
        +Long id
        +Long orderId
        +Long productId
        +String productName
        +String brandName
        +BigDecimal price
        +Integer quantity
        +BigDecimal totalPrice
        +LocalDateTime createdAt
        --
        +OrderItem(orderId, productId, productName, brandName, price, quantity)
        +calculateTotalPrice() BigDecimal
    }

    class Payment {
        +Long id
        +Long orderId
        +Long userId
        +BigDecimal amount
        +PaymentStatus status
        +PaymentMethod method
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
        --
        +Payment(orderId, userId, amount, method)
        +complete() void
        +fail() void
        +cancel() void
        +isCompleted() boolean
        +isCancellable() boolean
    }

    class OrderStatus {
        <<enumeration>>
        COMPLETED
        CANCELLED
    }

    class PaymentStatus {
        <<enumeration>>
        PENDING
        COMPLETED
        FAILED
        CANCELLED
    }

    class PaymentMethod {
        <<enumeration>>
        POINT
    }

    Point --> User : userId
    PointTransaction --> User : userId
    PointTransaction --> Order : orderId (optional)
    PointTransaction --> TransactionType : has
    Product --> Brand : brandId
    Stock --> Product : productId
    Like --> User : userId
    Like --> Product : productId
    Order --> User : userId
    OrderItem --> Order : orderId
    OrderItem --> Product : productId (스냅샷)
    Payment --> Order : orderId
    Payment --> User : userId
    Order --> OrderStatus : has
    Payment --> PaymentStatus : has
    Payment --> PaymentMethod : has
```
---

## 계층별 클래스 구조 (Clean Architecture 기반)

### Controller Layer (interfaces/api)

```mermaid
classDiagram
    class UserV1Controller {
        -UserFacade userFacade
        --
        +getUser(userId: Long) ApiResponse~UserResponse~
        +getUsers() ApiResponse~List~UserResponse~~
    }

    class PointV1Controller {
        -PointFacade pointFacade
        --
        +getMyPoint(userId: Long) ApiResponse~PointResponse~
        +chargePoint(userId: Long, request: ChargePointRequest) ApiResponse~PointResponse~
        +getTransactionHistory(userId: Long) ApiResponse~List~PointTransactionResponse~~
    }

    class StockV1Controller {
        -StockFacade stockFacade
        --
        +getStock(productId: Long) ApiResponse~StockResponse~
        +increaseStock(productId: Long, request: IncreaseStockRequest) ApiResponse~StockResponse~
    }

    class PaymentV1Controller {
        -PaymentFacade paymentFacade
        --
        +getPayment(orderId: Long) ApiResponse~PaymentResponse~
    }

    class BrandV1Controller {
        -BrandFacade brandFacade
        --
        +getBrand(brandId: Long) ApiResponse~BrandResponse~
        +getBrands() ApiResponse~List~BrandResponse~~
    }

    class ProductV1Controller {
        -ProductFacade productFacade
        --
        +getProduct(productId: Long) ApiResponse~ProductDetailResponse~
        +getProducts() ApiResponse~List~ProductResponse~~
    }

    class LikeV1Controller {
        -LikeFacade likeFacade
        --
        +toggleLike(userId: Long, request: ToggleLikeRequest) ApiResponse~LikeResponse~
        +getMyLikes(userId: Long) ApiResponse~List~LikedProductResponse~~
    }

    class OrderV1Controller {
        -OrderFacade orderFacade
        --
        +createOrder(userId: Long, request: CreateOrderRequest) ApiResponse~OrderResponse~
        +getMyOrders(userId: Long) ApiResponse~List~OrderSummaryResponse~~
        +getOrderDetail(userId: Long, orderId: Long) ApiResponse~OrderDetailResponse~
    }
```

---

### Facade Layer (application)

```mermaid
classDiagram
    class UserFacade {
        -UserService userService
        --
        +getUser(userId: Long) UserInfo
        +getUsers() List~UserInfo~
    }

    class PointFacade {
        -PointService pointService
        --
        +getMyPoint(userId: Long) PointInfo
        +chargePoint(userId: Long, amount: BigDecimal) PointInfo
        +getTransactionHistory(userId: Long) List~PointTransactionInfo~
    }

    class StockFacade {
        -StockService stockService
        --
        +getStock(productId: Long) StockInfo
        +increaseStock(productId: Long, amount: Int) StockInfo
    }

    class PaymentFacade {
        -PaymentService paymentService
        --
        +getPayment(orderId: Long) PaymentInfo
    }

    class BrandFacade {
        -BrandService brandService
        --
        +getBrand(brandId: Long) BrandInfo
        +getBrands() List~BrandInfo~
    }

    class ProductFacade {
        -ProductService productService
        --
        +getProduct(productId: Long) ProductDetailInfo
        +getProducts() List~ProductInfo~
    }

    class LikeFacade {
        -LikeService likeService
        --
        +toggleLike(userId: Long, productId: Long) LikeInfo
        +getMyLikes(userId: Long) List~LikedProductInfo~
    }

    class OrderFacade {
        -OrderService orderService
        --
        +createOrder(userId: Long, command: CreateOrderCommand) OrderInfo
        +getMyOrders(userId: Long) List~OrderSummaryInfo~
        +getOrderDetail(userId: Long, orderId: Long) OrderDetailInfo
    }
```

---

### Service Layer (domain)

```mermaid
classDiagram
    class UserService {
        -UserRepository userRepository
        --
        +getUser(userId: Long) User
        +getUsers() List~User~
    }

    class PointService {
        -PointRepository pointRepository
        -PointTransactionRepository pointTransactionRepository
        --
        +getPoint(userId: Long) Point
        +chargePoint(userId: Long, amount: BigDecimal) Point
        +deductPoint(userId: Long, amount: BigDecimal) Point
        +getTransactionHistory(userId: Long) List~PointTransaction~
    }

    class StockService {
        -StockRepository stockRepository
        --
        +getStock(productId: Long) Stock
        +increaseStock(productId: Long, amount: Int) Stock
        +deductStock(productId: Long, amount: Int) Stock
    }

    class PaymentService {
        -PaymentRepository paymentRepository
        -PointService pointService
        --
        +createPayment(orderId: Long, userId: Long, amount: BigDecimal) Payment
        +getPayment(orderId: Long) Payment
        +cancelPayment(orderId: Long) Payment
    }

    class BrandService {
        -BrandRepository brandRepository
        --
        +getBrand(brandId: Long) Brand
        +getBrands() List~Brand~
    }

    class ProductService {
        -ProductRepository productRepository
        -BrandRepository brandRepository
        --
        +getProduct(productId: Long) Product
        +getProducts() List~Product~
        +getProductWithBrand(productId: Long) Pair~Product, Brand~
    }

    class LikeService {
        -LikeRepository likeRepository
        -ProductRepository productRepository
        --
        +toggleLike(userId: Long, productId: Long) Like?
        +getMyLikes(userId: Long) List~Like~
        +isLiked(userId: Long, productId: Long) Boolean
    }

    class OrderService {
        -OrderRepository orderRepository
        -OrderItemRepository orderItemRepository
        -ProductRepository productRepository
        -PaymentService paymentService
        -StockService stockService
        --
        +createOrder(userId: Long, items: List~OrderItemCommand~) Order
        +getMyOrders(userId: Long) List~Order~
        +getOrderDetail(userId: Long, orderId: Long) Order
        +getOrderItems(orderId: Long) List~OrderItem~
    }
```

---

### Repository 인터페이스 (domain)

```mermaid
classDiagram
    class UserRepository {
        <<interface>>
        +findById(id: Long) User?
        +findAll() List~User~
        +save(user: User) User
    }

    class PointRepository {
        <<interface>>
        +findByUserId(userId: Long) Point?
        +findByUserIdWithLock(userId: Long) Point?
        +save(point: Point) Point
    }

    class PointTransactionRepository {
        <<interface>>
        +findByUserId(userId: Long) List~PointTransaction~
        +findByOrderId(orderId: Long) List~PointTransaction~
        +save(transaction: PointTransaction) PointTransaction
    }

    class StockRepository {
        <<interface>>
        +findByProductId(productId: Long) Stock?
        +findByProductIdWithLock(productId: Long) Stock?
        +save(stock: Stock) Stock
    }

    class PaymentRepository {
        <<interface>>
        +findById(id: Long) Payment?
        +findByOrderId(orderId: Long) Payment?
        +save(payment: Payment) Payment
    }

    class BrandRepository {
        <<interface>>
        +findById(id: Long) Brand?
        +findAll() List~Brand~
        +save(brand: Brand) Brand
    }

    class ProductRepository {
        <<interface>>
        +findById(id: Long) Product?
        +findByIdWithLock(id: Long) Product?
        +findAll() List~Product~
        +findByBrandId(brandId: Long) List~Product~
        +existsById(id: Long) Boolean
        +save(product: Product) Product
    }

    class LikeRepository {
        <<interface>>
        +findByUserIdAndProductId(userId: Long, productId: Long) Like?
        +findByUserId(userId: Long) List~Like~
        +existsByUserIdAndProductId(userId: Long, productId: Long) Boolean
        +save(like: Like) Like
        +delete(like: Like) void
    }

    class OrderRepository {
        <<interface>>
        +findById(id: Long) Order?
        +findByUserId(userId: Long) List~Order~
        +findByUserIdOrderByCreatedAtDesc(userId: Long) List~Order~
        +save(order: Order) Order
    }

    class OrderItemRepository {
        <<interface>>
        +findByOrderId(orderId: Long) List~OrderItem~
        +save(orderItem: OrderItem) OrderItem
    }
```
