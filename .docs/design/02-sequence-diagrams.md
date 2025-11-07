# 02. 시퀀스 다이어그램

## 1. 상품 목록 조회 (필터링 및 정렬)

### 시나리오
사용자가 브랜드 필터링과 정렬 옵션을 적용하여 상품 목록을 조회합니다.

```mermaid
sequenceDiagram
    participant Client
    participant ProductV1Controller
    participant ProductFacade
    participant ProductService
    participant ProductRepository

    Client->>ProductV1Controller: GET /api/v1/products?brandId=1&sort=latest&page=0&size=20

    ProductV1Controller->>ProductV1Controller: 요청 파라미터 검증

    alt X-USER-ID 헤더 누락
        ProductV1Controller-->>Client: ApiResponse(400, "X-USER-ID 헤더 정보 누락입니다.")
    else 유효하지 않은 정렬 옵션
        ProductV1Controller-->>Client: ApiResponse(400, "유효하지 않은 정렬 옵션입니다.")
    else 유효하지 않은 페이지 파라미터 (음수)
        ProductV1Controller-->>Client: ApiResponse(400, "유효하지 않은 페이지 파라미터입니다.")
    else 정상 요청
        ProductV1Controller->>ProductFacade: getProducts(brandId, sort, pageable)

        ProductFacade->>ProductService: getProducts(brandId, sort, pageable)

        ProductService->>ProductRepository: findAll(brandId, sort, pageable)
        Note over ProductRepository: 브랜드 필터링 적용<br/>정렬 옵션 적용 (latest/price_asc/likes_desc)<br/>likes_count는 Product 엔티티에 비정규화

        ProductRepository-->>ProductService: Page<Product>

        ProductService-->>ProductFacade: Page<Product>

        ProductFacade-->>ProductV1Controller: Page<ProductInfo>
        ProductV1Controller-->>Client: ApiResponse<Page<ProductResponse>>(200, success)
    end
```

---

## 2. 내가 좋아요한 상품 목록 조회

### 시나리오
사용자가 자신이 좋아요한 상품 목록을 조회합니다.

```mermaid
sequenceDiagram
    participant Client
    participant LikeV1Controller
    participant LikeFacade
    participant LikeService
    participant LikeRepository

    Client->>LikeV1Controller: GET /api/v1/like/products?page=0&size=20
    Note over Client,LikeV1Controller: Header: X-USER-ID

    LikeV1Controller->>LikeV1Controller: X-USER-ID 헤더 검증

    alt X-USER-ID 헤더 누락
        LikeV1Controller-->>Client: ApiResponse(400, "X-USER-ID 헤더 정보 누락입니다.")
    else 유효하지 않은 페이지 파라미터 (음수)
        ProductV1Controller-->>Client: ApiResponse(400, "유효하지 않은 페이지 파라미터입니다.")
    else 정상 요청
        LikeV1Controller->>LikeFacade: getMyLikes(memberId, pageable)

        LikeFacade->>LikeService: getMyLikes(memberId, pageable)

        LikeService->>LikeRepository: findByMemberId(memberId, pageable)
        Note over LikeRepository: Member ID로 좋아요 목록 조회<br/>페이지네이션 적용
        LikeRepository-->>LikeService: Page<Like>

        Note over LikeService: Like 엔티티에서<br/>상품 정보, 브랜드명,<br/>좋아요 등록 시간 추출

        LikeService-->>LikeFacade: Page<LikeInfo>

        LikeFacade-->>LikeV1Controller: Page<LikeInfo>
        LikeV1Controller-->>Client: ApiResponse<Page<LikeResponse>>(200, success)
    end
```

---


## 3. 상품 좋아요 등록 (멱등 동작)

### 시나리오
사용자가 상품에 좋아요를 등록하는 흐름입니다.
멱등성을 보장하여 같은 요청이 여러 번 와도 동일한 결과를 반환합니다.

```mermaid
sequenceDiagram
    participant Client
    participant LikeV1Controller
    participant LikeFacade
    participant LikeService
    participant ProductService
    participant LikeRepository
    participant Product

    Client->>LikeV1Controller: POST /api/v1/like/products/{productId}
    Note over Client,LikeV1Controller: Header: X-USER-ID

    LikeV1Controller->>LikeV1Controller: X-USER-ID 헤더 검증

    alt X-USER-ID 헤더 누락
        LikeV1Controller-->>Client: ApiResponse(400, "X-USER-ID 헤더 정보 누락입니다.")
    else 정상 요청
        LikeV1Controller->>LikeFacade: toggleLike(command)

        LikeFacade->>ProductService: getProduct(productId)

        alt 상품이 존재하지 않음
            ProductService-->>LikeFacade: throw ProductNotFoundException
            LikeFacade-->>LikeV1Controller: Exception
            LikeV1Controller-->>Client: ApiResponse(404, "상품을 찾을 수 없습니다")
        else 상품 존재
            ProductService-->>LikeFacade: Product

            LikeFacade->>LikeService: toggleLike(memberId, productId)

            LikeService->>LikeRepository: findByMemberIdAndProductId(memberId, productId)

            alt 좋아요가 존재하지 않는 경우
                LikeRepository-->>LikeService: null
                LikeService->>LikeRepository: save(Like)
                LikeService->>Product: increaseLikeCount()
                Note over Product: 좋아요 수 증가
                LikeService-->>LikeFacade: LikeInfo
            else 좋아요가 이미 존재하는 경우 (멱등)
                LikeRepository-->>LikeService: Like
                Note over LikeService: 중복 등록 방지 (멱등성)
                LikeService-->>LikeFacade: LikeInfo
            end

            LikeFacade-->>LikeV1Controller: LikeInfo
            LikeV1Controller-->>Client: ApiResponse<LikeResponse>(200, success)
        end
    end

```

---

## 4. 상품 좋아요 취소 (멱등 동작)

```mermaid
sequenceDiagram
    participant Client
    participant LikeV1Controller
    participant LikeFacade
    participant LikeService
    participant ProductService
    participant LikeRepository
    participant Product

    Client->>LikeV1Controller: DELETE /api/v1/like/products/{productId}
    Note over Client,LikeV1Controller: Header: X-USER-ID

    LikeV1Controller->>LikeV1Controller: X-USER-ID 헤더 검증

    alt X-USER-ID 헤더 누락
        LikeV1Controller-->>Client: ApiResponse(400, "X-USER-ID 헤더 정보 누락입니다.")
    else 정상 요청
        LikeV1Controller->>LikeFacade: cancelLike(memberId, productId)

        LikeFacade->>ProductService: getProduct(productId)

        alt 상품이 존재하지 않음
            ProductService-->>LikeFacade: throw ProductNotFoundException
            LikeFacade-->>LikeV1Controller: Exception
            LikeV1Controller-->>Client: ApiResponse(404, "상품을 찾을 수 없습니다")
        else 상품 존재
            ProductService-->>LikeFacade: Product

            LikeFacade->>LikeService: cancelLike(memberId, productId)

            LikeService->>LikeRepository: findByMemberIdAndProductId(memberId, productId)

            alt 좋아요가 존재하는 경우
                LikeRepository-->>LikeService: Like
                LikeService->>LikeRepository: delete(like)
                LikeService->>Product: decreaseLikeCount()
                Note over Product: 좋아요 수 감소
                LikeService-->>LikeFacade: void
            else 좋아요가 존재하지 않는 경우 (멱등)
                LikeRepository-->>LikeService: null
                Note over LikeService: 이미 취소됨 (멱등성)
                LikeService-->>LikeFacade: void
            end

            LikeFacade-->>LikeV1Controller: void
            LikeV1Controller-->>Client: ApiResponse(200, success)
        end
    end
```
