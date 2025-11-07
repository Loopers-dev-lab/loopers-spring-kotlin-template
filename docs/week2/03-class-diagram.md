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
        +Integer stockQuantity
        +Long brandId
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
        --
        +Product(name, description, price, stock, brandId)
        +deductStock(quantity) void
        +isOutOfStock() boolean
        +validateStock(quantity) void
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

    class OrderStatus {
        <<enumeration>>
        COMPLETED
        CANCELLED
    }

    Product --> Brand : brandId
    Like --> Product : productId
    OrderItem --> Order : orderId
    OrderItem --> Product : productId (스냅샷)
    Order --> OrderStatus : has
```
---

## 계층별 클래스 구조 (Clean Architecture 기반)

### Controller Layer (interfaces/api)

```mermaid
classDiagram
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
