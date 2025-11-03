# 시퀀스 다이어그램

주요 기능의 객체 간 협력 구조를 시각화

1. 상품 목록 조회
2. 상품 좋아요 등록
3. 상품 좋아요 취소
4. 주문 생성
5. 주문 상세 조회

## 1. 상품 목록 조회

- 사용자가 상품 목록을 조회하는 흐름
- 브랜드 필터링, 정렬 기준, 페이징을 지원

```mermaid
sequenceDiagram
    participant User
    participant ProductController
    participant ProductService
    participant LikeFacade

    User->>ProductController: GET /products?brandId=1&sort=likes_desc&page=0&size=20
    ProductController->>ProductService: getProducts(brandId, sort, page, size)

    Note over ProductService: Product BC 내부에서<br/>상품 목록 조회

    loop 각 상품에 대해
        ProductService->>LikeFacade: countByProductId(productId)
        Note over LikeFacade: Like BC
        LikeFacade-->>ProductService: likeCount
    end

    ProductService-->>ProductController: ProductListResponse (with likeCount)
    ProductController-->>User: 200 OK (상품 목록 + 좋아요 수)
```

- **주요 책임**
  - `ProductController`: 요청 검증 및 응답 변환
  - `ProductService`: 비즈니스 로직 조율 (Product BC 내부)
  - `LikeFacade`: Like BC의 공개 인터페이스
- **설계 포인트**
  - **BC 간 협력**: Product BC는 Like BC의 공개 인터페이스(LikeFacade)를 통해서만 협력
  - **캡슐화**: 각 BC의 내부 구현(Repository, 도메인 로직 등)은 Facade 뒤에 숨겨짐
  - 좋아요 수는 별도 집계 (N+1 문제 고려 필요)

## 2. 상품 좋아요 등록

- 사용자가 상품에 좋아요를 등록하는 흐름
- 멱등성을 보장

```mermaid
sequenceDiagram
    participant User
    participant LikeController
    participant LikeService
    participant ProductFacade

    User->>LikeController: POST /like/products/{productId}
    Note over LikeController: X-USER-ID 헤더로 사용자 식별

    LikeController->>LikeService: addLike(userId, productId)

    LikeService->>ProductFacade: getById(productId)
    Note over ProductFacade: Product BC
    alt 상품이 존재하지 않음
        ProductFacade-->>LikeService: ProductNotFoundException
        LikeService-->>LikeController: 404 Error
        LikeController-->>User: 404 Not Found
    end

    ProductFacade-->>LikeService: Product

    Note over LikeService: Like BC 내부에서<br/>중복 확인 및 저장<br/>(멱등성 보장)

    LikeService-->>LikeController: 좋아요 등록 완료
    LikeController-->>User: 200 OK
```

- **주요 책임**
  - `LikeController`: 사용자 인증 및 요청 처리
  - `LikeService`: 좋아요 비즈니스 로직 (Like BC 내부)
  - `ProductFacade`: Product BC의 공개 인터페이스
- **설계 포인트**
  - **BC 간 협력**: Like BC는 Product BC의 공개 인터페이스(ProductFacade)를 통해 상품 존재 여부 확인
  - **캡슐화**: Like BC 내부의 중복 확인, 저장 로직은 Facade 뒤에 숨겨짐
  - 멱등성 보장: 이미 좋아요한 경우 중복 저장하지 않음

## 3. 상품 좋아요 취소

- 사용자가 상품에 대한 좋아요를 취소하는 흐름
- 멱등성을 보장

```mermaid
sequenceDiagram
    participant User
    participant LikeController
    participant LikeService
    participant ProductFacade

    User->>LikeController: DELETE /like/products/{productId}
    Note over LikeController: X-USER-ID 헤더로 사용자 식별

    LikeController->>LikeService: removeLike(userId, productId)

    LikeService->>ProductFacade: getById(productId)
    Note over ProductFacade: Product BC
    alt 상품이 존재하지 않음
        ProductFacade-->>LikeService: ProductNotFoundException
        LikeService-->>LikeController: 404 Error
        LikeController-->>User: 404 Not Found
    end

    ProductFacade-->>LikeService: Product

    Note over LikeService: Like BC 내부에서<br/>존재 확인 및 삭제<br/>(멱등성 보장)

    LikeService-->>LikeController: 좋아요 취소 완료
    LikeController-->>User: 200 OK
```

- **주요 책임**
  - `LikeController`: 사용자 인증 및 요청 처리
  - `LikeService`: 좋아요 비즈니스 로직 (Like BC 내부)
  - `ProductFacade`: Product BC의 공개 인터페이스
- **설계 포인트**
  - **BC 간 협력**: Like BC는 Product BC의 공개 인터페이스(ProductFacade)를 통해 상품 존재 여부 확인
  - **캡슐화**: Like BC 내부의 존재 확인, 삭제 로직은 Facade 뒤에 숨겨짐
  - 멱등성 보장: 이미 취소된 경우에도 에러 없이 성공 응답

## 4. 주문 생성

- 사용자가 여러 상품을 주문하는 흐름
- 재고 차감, 포인트 차감, 외부 시스템 연동을 포함

```mermaid
sequenceDiagram
    participant User
    participant OrderController
    participant OrderService
    participant ProductFacade
    participant PointFacade
    participant ExternalOrderSystem

    User->>OrderController: POST /orders {items: [{productId, quantity}]}
    Note over OrderController: X-USER-ID 헤더로 사용자 식별

    OrderController->>OrderService: createOrder(userId, orderRequest)

    Note over OrderService: 주문 요청 검증

    loop 각 주문 항목
        OrderService->>ProductFacade: getById(productId)
        Note over ProductFacade: Product BC
        alt 상품이 존재하지 않음
            ProductFacade-->>OrderService: ProductNotFoundException
            OrderService-->>OrderController: 404 Error
            OrderController-->>User: 404 Not Found
        end
        ProductFacade-->>OrderService: Product

        OrderService->>ProductFacade: checkStock(productId, quantity)
        alt 재고 부족
            ProductFacade-->>OrderService: InsufficientStockException
            OrderService-->>OrderController: 400 Error (재고 부족)
            OrderController-->>User: 400 Bad Request
        end
    end

    Note over OrderService: 총 주문 금액 계산

    OrderService->>PointFacade: checkBalance(userId, totalAmount)
    Note over PointFacade: Point BC
    alt 포인트 부족
        PointFacade-->>OrderService: InsufficientPointException
        OrderService-->>OrderController: 400 Error (포인트 부족)
        OrderController-->>User: 400 Bad Request
    end

    Note over OrderService: 트랜잭션 시작<br/><br/>Order BC 내부에서<br/>주문 저장

    loop 각 주문 항목
        OrderService->>ProductFacade: decreaseStock(productId, quantity)
        Note over ProductFacade: Product BC에서<br/>재고 차감
    end

    OrderService->>PointFacade: deductPoints(userId, totalAmount)
    Note over PointFacade: Point BC에서<br/>포인트 차감

    Note over OrderService: 트랜잭션 커밋

    OrderService->>ExternalOrderSystem: sendOrderInfo(order)
    Note over ExternalOrderSystem: 외부 시스템<br/>(비동기 또는 Mock)

    OrderService-->>OrderController: OrderResponse
    OrderController-->>User: 200 OK (주문 정보)
```

- **주요 책임**
  - `OrderController`: 요청 검증 및 응답 변환
  - `OrderService`: 주문 비즈니스 로직 조율 (Order BC 내부)
  - `ProductFacade`: Product BC의 공개 인터페이스
  - `PointFacade`: Point BC의 공개 인터페이스
  - `ExternalOrderSystem`: 외부 시스템 연동
- **설계 포인트**
  - **BC 간 협력**: Order BC는 Product BC와 Point BC의 공개 인터페이스(Facade)를 통해서만 협력
  - **캡슐화**: 각 BC의 내부 구현(주문 저장, 재고 차감, 포인트 차감 로직)은 Facade 뒤에 숨겨짐
  - **Stock은 Product BC의 일부**: 재고 관련 작업도 ProductFacade를 통해 처리
  - **트랜잭션 범위**: 주문 저장, 재고 차감, 포인트 차감이 하나의 트랜잭션으로 관리됨
  - **외부 시스템 연동**: 트랜잭션 외부에서 처리하여 성능 및 안정성 확보
  - **사전 검증**: 상품 존재, 재고 확인, 포인트 확인으로 트랜잭션 실패 최소화

## 5. 주문 상세 조회

- 사용자가 자신의 주문 상세 정보를 조회하는 흐름

```mermaid
sequenceDiagram
    participant User
    participant OrderController
    participant OrderService

    User->>OrderController: GET /orders/{orderId}
    Note over OrderController: X-USER-ID 헤더로 사용자 식별

    OrderController->>OrderService: getOrderDetail(userId, orderId)

    Note over OrderService: Order BC 내부에서<br/>주문 조회 및<br/>소유자 검증

    alt 주문이 존재하지 않음
        OrderService-->>OrderController: 404 Error
        OrderController-->>User: 404 Not Found
    end

    alt 다른 사용자의 주문
        OrderService-->>OrderController: 403 Forbidden
        OrderController-->>User: 403 Forbidden
    end

    OrderService-->>OrderController: OrderDetailResponse
    OrderController-->>User: 200 OK (주문 상세 정보)
```

- **주요 책임**
  - `OrderController`: 요청 검증 및 응답 변환
  - `OrderService`: 주문 조회 및 권한 검증 (Order BC 내부)
- **설계 포인트**
  - **캡슐화**: Order BC 내부의 조회 로직, 권한 검증 로직은 Service 내부에 캡슐화
  - **권한 검증**: 본인 주문만 조회 가능
  - **응답 데이터**: 주문 항목(OrderItems) 정보 포함
