# 시퀀스 다이어그램 (Sequence Diagrams)

본 문서는 감성 이커머스의 핵심 유스케이스에 대한 **시스템 아키텍처 관점**의 상호작용을 시각화합니다.

---

## 1. 상품 목록 조회 (Find Products)

### 1.1 시스템 컨텍스트 다이어그램

```mermaid
sequenceDiagram
    actor User
    participant API
    participant ProductFacade
    participant ProductService
    participant DB
    User ->>+ API: GET /products?sort=latest
    note over API: DTO 검증 및 변환
    API ->>+ ProductFacade: 상품 목록 조회 요청
    ProductFacade ->>+ ProductService: 비즈니스 로직 실행

    rect rgb(240, 255, 240)
        note over ProductService, DB: 조회 최적화 (N+1 방지)
        ProductService ->>+ DB: 1. 상품 목록 조회<br/>(페이징 20개)
        DB -->>- ProductService: Products
        ProductService ->>+ DB: 2. 통계 일괄 조회<br/>(IN clause)
        DB -->>- ProductService: Statistics
        ProductService ->>+ DB: 3. 브랜드 일괄 조회<br/>(IN clause)
        DB -->>- ProductService: Brands
        note over ProductService: 메모리에서 조합<br/>(Product + Statistic + Brand)
    end

    ProductService -->>- ProductFacade: ProductView 목록
    note over ProductFacade: 트랜잭션 커밋
    ProductFacade -->>- API: 응답 DTO 변환
    API -->>- User: 200 OK<br/>상품 목록 (JSON)
```

---

## 2. 상품 좋아요 등록 (Like Product)

```mermaid
sequenceDiagram
    actor User
    participant API
    participant LikeFacade
    participant LikeService as ProductLikeService
    participant ProductService
    participant DB
    User ->>+ API: POST /products/{id}/likes
    note over API: 사용자 인증 확인
    API ->>+ LikeFacade: 좋아요 등록 요청
    LikeFacade ->>+ LikeService: addLike(userId, productId)
    LikeService ->>+ DB: UPSERT ProductLike
    note right of DB: INSERT ... ON DUPLICATE KEY UPDATE<br/>또는<br/>INSERT ... ON CONFLICT DO NOTHING

    alt 새로 추가됨 (affected rows = 1)
        DB -->> LikeService: affected = 1
        LikeService -->> LikeFacade: AddLike(changed=true)
        note over LikeFacade: 변경 발생 → 통계 업데이트
        LikeFacade ->>+ ProductService: increaseProductLikeCount(productId)
        ProductService ->>+ DB: 통계 업데이트
        note right of DB: UPDATE product_statistics<br/>SET like_count = like_count + 1
        DB -->>- ProductService: 성공
        ProductService -->>- LikeFacade: 완료
    else 이미 존재함 (affected rows = 0)
        DB -->> LikeService: affected = 0
        LikeService -->> LikeFacade: AddLike(changed=false)
        note over LikeFacade: 변경 없음 → 통계 유지<br/>(멱등성 보장)
    end

    LikeService -->>- LikeFacade: 완료
    note over LikeFacade: 트랜잭션 커밋
    LikeFacade -->>- API: 성공
    API -->>- User: 200 OK
```

- 특이 사항
    - UPSERT 사용: INSERT ... ON DUPLICATE KEY UPDATE 또는 ON CONFLICT DO NOTHING
    - 반환값 기반 판단: affected rows로 실제 변경 여부 확인
    - 멱등성 보장: 중복 등록 시 통계 증가 없이 성공 응답

---

## 3. 주문 생성 및 결제 (Place Order)

```mermaid
sequenceDiagram
    actor User
    participant API
    participant OrderFacade
    participant ProductService
    participant PointService
    participant OrderService
    participant DB
    User ->>+ API: POST /orders<br/>{items, usePoint}
    note over API: DTO 검증<br/>상품 ID, 수량, 포인트
    API ->>+ OrderFacade: 주문 생성 요청
    note over OrderFacade: 트랜잭션 시작
    note over OrderFacade, DB: 1단계: 재고 차감
    OrderFacade ->>+ ProductService: 재고 차감 요청
    ProductService ->>+ DB: 비관적 락 조회
    note right of DB: SELECT ... FOR UPDATE<br/>(동시성 제어)
    DB -->> ProductService: Locked Products

    loop 각 상품
        ProductService ->> ProductService: decreaseStock()<br/>재고 검증<br/>상태 업데이트
    end

    ProductService ->> DB: UPDATE products<br/>SET stock = ..., status = ...
    DB -->>- ProductService: 성공
    ProductService -->>- OrderFacade: 재고 차감 완료
    note over OrderFacade, DB: 2단계: 포인트 차감
    OrderFacade ->>+ PointService: 포인트 차감 요청
    PointService ->>+ DB: 비관적 락 조회
    note right of DB: SELECT ... FOR UPDATE
    DB -->> PointService: Locked PointAccount
    PointService ->> PointService: deduct(amount)<br/>잔액 검증<br/>차감 처리
    PointService ->> DB: UPDATE point_accounts<br/>SET balance = balance - ?
    DB -->>- PointService: 성공
    PointService -->>- OrderFacade: 포인트 차감 완료
    note over OrderFacade, DB: 3단계: 상품 정보 조회
    OrderFacade ->>+ ProductService: 상품 정보 조회<br/>(가격, 이름)
    ProductService ->> DB: SELECT ... WHERE id IN (...)
    DB -->> ProductService: Products
    ProductService -->>- OrderFacade: 상품 정보
    note over OrderFacade: OrderItem 생성<br/>(스냅샷 저장)
    note over OrderFacade, DB: 4단계: 주문 및 결제 생성
    OrderFacade ->>+ OrderService: 주문 생성
    OrderService ->> OrderService: Order.paid()<br/>(PAID 상태)
    OrderService ->> DB: INSERT INTO orders
    DB -->> OrderService: Order 생성
    OrderService ->> OrderService: Payment.paid()<br/>(전액 포인트 검증)
    OrderService ->> DB: INSERT INTO payments
    DB -->> OrderService: Payment 생성
    OrderService -->>- OrderFacade: 주문 완료
    note over OrderFacade: 트랜잭션 커밋<br/>(모두 성공 or 전체 롤백)
    OrderFacade -->>- API: orderId
    API -->>- User: 200 Created<br/>{orderId}
```

- 특이 사항
    - 재고와 포인트 모두 비관적 락 사용
    - 주문/결제 모두 성공 or 전체 롤백

- 실패 시나리오
    - 재고 부족 → 트랜잭션 롤백 → 400 Bad Request
    - 포인트 부족 → 트랜잭션 롤백 → 400 Bad Request
    - 상품 미존재 → 트랜잭션 롤백 → 400 Bad Request