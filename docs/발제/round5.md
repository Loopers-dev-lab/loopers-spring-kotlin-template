## 🧭 루프팩 BE L2 - Round 5

> 실제 서비스에서 **느린 조회** 문제를 추적하고 **인덱스와 캐시, 구조 개선을 통해 읽기 성능을 향상**시키는 실전 전략을 학습합니다.
>

<aside>
🎯

**Summary**

</aside>

- 상품 목록 조회가 느릴 때, 병목의 원인을 파악하고 실제 성능을 개선하는 구조를 학습합니다.
- 인덱스와 캐시를 중심으로, 읽기 병목 문제를 추적하고 실전 방식으로 해결해봅니다.
- 단순한 속도 개선이 아닌, **구조적 개선과 유지보수 가능한 설계**를 함께 고민합니다.
- 조회 성능 향상을 위한 캐시 설계, TTL 설정, 무효화 전략의 실전 감각을 익힙니다.

<aside>
📌

**Keywords**

</aside>

- 인덱스 설계
- Redis 캐시
- 조회 병목 분석
- 정렬 및 필터 최적화

<aside>
🧠

**Learning**

</aside>

## 🚧 실무에서 겪는 읽기 성능 문제들

> 💬 트래픽이 많아지면 **쓰기보다 읽기가 문제다**는 말이 있을 정도로 읽기 병목은 자주 발생합니다.
>
>
> 서비스의 이용자가 많아질수록 **읽기 연산은 쓰기 연산의 수 배~수십 배로 증가**하고 이는 곧 **사용자 경험(UX)과 직결되는 성능 이슈**로 이어집니다.
>

---

## 🔎 인덱스 설계와 조회 최적화

### 🚧 실무에서 겪는 문제

- 상품 목록을 `brandId`로 필터하고, `price_asc`로 정렬하는데 너무 느리다
- `likes_desc` 정렬 시 성능이 급락하거나, 인덱스를 써도 **효과가 없음**
- 페이지네이션이 붙으면 `OFFSET` 처리로 지연이 누적됨
- 인덱스가 있어도 **조건 순서가 맞지 않으면 전혀 사용되지 않음**

### 🧱 Index On RDB

> 이번 주제에서 말하는 인덱스는 **MySQL, PostgreSQL 등 RDB에서 사용하는 인덱스**입니다. 이 인덱스는 대부분 **B-Tree 기반**으로 동작하며, **WHERE 절**, **정렬**, **JOIN** 등에 사용되어 성능을 극적으로 개선할 수 있습니다.

📚 전체 테이블을 일일이 탐색하지 않고, 정렬된 값의 **책갈피**를 이용해 필요한 데이터에 빠르게 접근하는 방식이라고 생각해보면 쉽습니다.
>

<aside>
💡

서점에서 **Java + Spring 에 관련된 책**만 찾으려면…

- 인덱스 없음 → 서점의 `IT` 섹션의 모든 책을 확인해야 함
- 인덱스 있음 → 각 책이 놓인 위치를 확보해, 각 책만 빠르게 찾을 수 있음
</aside>

### 🧠 단일 vs 복합 인덱스

**✅ 단일 인덱스**

```sql
CREATE INDEX idx_brand_id ON products(brand_id);
```

- brandId 필터엔 빠름, 정렬(price)까지 커버하진 못함

**✅ 복합 인덱스**

```sql
CREATE INDEX idx_brand_price ON products(brand_id, price);
```

- brandId + price 정렬까지 함께 커버 가능
- 조건이 “왼쪽 → 오른쪽 순서”로 사용될 때만 효과 있음

| 인덱스 | WHERE brandId | ORDER BY price |
| --- | --- | --- |
| (brand_id) | ✅ | ❌ |
| (brand_id, price) | ✅ | ✅ |
| (price, brand_id) | ❌ | ❌ |

### 🧪 EXPLAIN - 쿼리 실행 계획 확인

```sql
EXPLAIN SELECT * FROM products
WHERE brand_id = 1
ORDER BY price ASC
LIMIT 20;
```

| 항목 | 의미 | 확인 포인트 |
| --- | --- | --- |
| key | 사용된 인덱스 이름 | null이면 인덱스 미사용 |
| type | 접근 방식 | index / range / ALL |
| rows | 예측된 스캔 행 수 | 낮을수록 좋음 |
| Extra | 추가 정보 | Using index / Using filesort 여부 |

> `Using filesort` 가 Extra 에 표시된다면 인덱스 정렬이 적용되지 않고, 정렬 연산이 추가 수행됨
>

### 🔄 실전 예시 비교

### ❌ 인덱스 없을 때

```sql
SELECT * FROM products
WHERE brand_id = 1
ORDER BY price ASC;
```

- rows: 10,000
- Extra: Using filesort

### ✅ 인덱스 추가 후

```sql
CREATE INDEX idx_brand_price ON products(brand_id, price);
```

```sql
SELECT * FROM products
WHERE brand_id = 1
ORDER BY price ASC;
```

- rows: 200
- Extra: Using index

### ⚠ 인덱스 설계 시 주의할 점

- 자주 변경되는 컬럼에 인덱스를 남발하면 **쓰기 성능 저하**
- **조건 순서, 필터/정렬 조합**을 고려해 복합 인덱스를 설계해야 함
- 모수가 작을 땐 오히려 인덱스 없이 **Full Scan이 더 빠를 수도 있음**

<aside>
❓

**(주의) MySQL 버전에 따른 인덱스와 정렬 방향**

**정렬 방향(ASC/DESC)** 은 MySQL 8.0 이상부터는 옵티마이저가 **단일 인덱스로도 양방향 정렬을 지원**합니다. 하지만 **MySQL 5.7 이하**에선 DESC 정렬 시 **추가 인덱스가 필요**하거나, `Using filesort`가 발생할 수 있으므로 주의가 필요합니다.

일부 정렬 방향에서 정렬 최적화를 보장하고 싶다면, 명시적으로 `ASC`, `DESC` 방향을 지정한 **인덱스 생성**도 고려할 수 있습니다.
(e.g. `CREATE INDEX idx_price_desc ON products (price DESC)`)

</aside>

### **카디널리티(중복도, 다양성)**

```jsx
Q. 인덱스를 카디널리티가 높은 컬럼에 걸어야할까? 낮은 컬럼에 걸어야할까?
-> 카디널리티가 높은 컬럼에 걸어야한다. 

Q. 성별, 주민등록번호중 어떤게 카디널리티가 높을까?
-> 주민등록번호가 카디널리티가 더 높다.

Q. Get API를 개발하는데 단건 조회, 유저가 좋아요한 상품중에 Price가 5000원 이상이면서 최근 30일 이내의 여성 상품
-> like table, Index(user_id, updated_at, price)

Q. 이름과 생년월일이 있어요. 카디널리티 뭐가 높죠?
-> 카디널리티라는건 내가보면 안다.

*현재 테이블의 카디널리티 정보를 뽑을수 있음. 글쓰기에 포함되면 좋을것 같아요.
```

---

## ❤️ 좋아요 수 정렬과 정규화 전략

### 🚧 실무에서 겪는 문제

- 상품 목록을 **좋아요 수 기준(likes_desc)** 으로 정렬하려는데 성능이 매우 느림
- `likes`는 별도 테이블이고, `products` 테이블에 **likeCount**가 없음
- 쿼리에서 집계(join + group by)하거나, 애플리케이션에서 count 쿼리를 반복 호출 → N+1 발생
- 상품이 많아질수록 쿼리 복잡도, 네트워크 비용, 정렬 비용이 급격히 증가

### 🧱 왜 문제가 발생할까?

**기본 구조**

- `products` 테이블 ← 상품 정보
- `likes` 테이블 ← (user_id, product_id) 조합

**정렬 쿼리 예시 (집계 방식)**

```sql
SELECT p.*, COUNT(l.id) AS like_count
FROM products p
LEFT JOIN likes l ON p.id = l.product_id
GROUP BY p.id
ORDER BY like_count DESC
LIMIT 20;
```

> 📉 **WARNING!** GROUP BY, 정렬, 조인 비용이 한 번에 들어감 → 인덱스로도 커버 어려움
>

### **❗** 대안 1: 의도적 비정규화 - 제품 테이블에 like_count 필드 유지

```sql
ALTER TABLE products ADD COLUMN like_count INT DEFAULT 0;
```

- 좋아요 등록/취소 시, `products.like_count`도 함께 갱신
- 정렬 시 단순 정렬로 처리 가능 → 성능 향상

```sql
SELECT * FROM products ORDER BY like_count DESC;
```

> ✅ **장점:** 매우 빠른 정렬, 인덱스 사용 가능
>
>
> ⚠ **단점:** 쓰기 시 동시성/정합성 문제 고려 필요
>

### ❗ **대안 2: 조회 전용 구조로 분리**

- 읽기 전용 테이블 또는 View에서 미리 집계한 좋아요 수 제공
- 좋아요 수를 주기적으로 적재 (예: 이벤트 기반, 배치 등)

```sql
CREATE TABLE product_likes_view (
  product_id BIGINT PRIMARY KEY,
  like_count INT
);
```

> ✅ **장점:** 조회/튜닝 용이, 정렬도 인덱스로 가능
>
>
> ⚠ **단점:** 실시간성 일부 희생, 별도 sync 로직 필요
>

### 🔍 설계 판단 기준

| 항목 | 정규화 유지 (조인 집계) | 비정규화 (likeCount 컬럼) |
| --- | --- | --- |
| 조회 성능 | ❌ 느림 | ✅ 빠름 |
| 쓰기 복잡도 | ✅ 단순 | ⚠ 증가 |
| 실시간성 | ✅ 완전 보장 | ⚠ 약간의 지연 가능 |
| 확장성 | ✅ 유연 | ❌ 단순 정렬만 가능 |

### 💡 실무 팁

- 정렬 기준으로 쓰는 값은 **조회 전용 구조 또는 비정규화 필드**로 따로 유지하는 경우가 많음
- 이벤트 기반으로 count를 갱신하거나, 일정 주기마다 적재하는 구조로 대응 가능
- 실시간 정합성보다 UX와 속도가 더 중요한 경우엔 과감히 비정규화를 선택하기도 함

---

## 🚄 캐시 전략 - 자주 조회되는 데이터를 빠르게 제공하기

### 🚧 실무에서 겪는 문제

- 인기 상품, 브랜드 목록, 가격 필터 등은 **항상 동일한 요청**이 반복됨
- 유저 수가 늘어날수록 동일 쿼리 요청도 수십 배로 증가
- 데이터는 자주 안 바뀌는데, **매번 DB에서 새로 조회**
- 응답 속도 문제뿐 아니라, **DB 부하까지 증가**해 전체 서비스에 영향

### 🧱 캐시는 왜 필요한가?

- **자주 요청되지만 자주 바뀌지 않는 데이터** 를 위한 구조적 최적화
- 응답 속도를 극적으로 줄이고, DB 요청 횟수를 획기적으로 감소시킴
- 조회 시점 기준으로 일정 시간 동안 **가장 최근에 봤거나 만들어진 결과** 를 제공

> 🧠 캐시는 결국 **정확도 ↔ 속도** 사이에서 균형을 선택하는 전략
>

### 🔍 TTL과 캐시 무효화 전략

| 전략 | 설명 | 사용 예 |
| --- | --- | --- |
| TTL (Time-To-Live) | 일정 시간 지나면 자동 만료 | 상품 상세 TTL 10분 |
| 수동 무효화 (@CacheEvict) | 특정 이벤트 발생 시 삭제 | 좋아요 눌렀을 때 상품 캐시 삭제 |
| Write-Through | 쓰기 시 캐시도 함께 갱신 | 포인트 충전, 장바구니 등 |
| Read-Through | 조회 시 캐시 없으면 DB 조회 + 캐시 저장 | 기본적인 @Cacheable 동작 방식 |
| Refresh-Ahead | 만료 전에 미리 새로고침 | 랭킹, 홈화면 등 주기 갱신이 필요한 경우 |

### 🧪 Spring 기반 캐시 활용 예제

### **✅ AOP 기반 @Cacheable 방식**

```java
@Service
public class ProductService {
		..
    @Cacheable(cacheNames = "productDetail", key = "#productId")
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
```

- 내부적으로 Spring AOP가 프록시를 생성하여 메서드 실행 전/후 캐시를 검사
- 호출 시 **캐시 Hit이면 메서드 실행 생략**, Miss면 실행 후 결과 저장
- **장점:** 코드가 간결하고 빠르게 도입 가능
- **단점:** 흐름이 추상화되어 있어 언제 어떤 타이밍에 캐싱되는지 체감이 어려움

### 🔍 직접 RedisTemplate 사용

```kotlin
@Service
public class ProductService {

    private final RedisTemplate<String, Product> redisTemplate;
    private final ProductRepository productRepository;

    public ProductService(RedisTemplate<String, Product> redisTemplate,
                          ProductRepository productRepository) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
    }

    public Product getProduct(Long productId) {
        String key = "product:detail:" + productId;

        // 1. Redis에서 먼저 조회
        Product cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }

        // 2. DB 조회
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        // 3. Redis에 캐시 저장 (10분 TTL)
        redisTemplate.opsForValue().set(key, product, Duration.ofMinutes(10));

        return product;
    }
}
```

- **직접 키를 만들고 TTL을 지정**하며 캐시 저장
- 흐름이 명시적이라 **캐시 동작을 눈으로 확인 가능**
- 실무에서도 복잡한 구조, 커스텀 캐시 처리 시 자주 사용됨

### 💡 정리: 언제 어떤 방식이 적절할까?

| 구분 | @Cacheable | RedisTemplate |
| --- | --- | --- |
| 도입 속도 | ✅ 빠름 | ❌ 설정 복잡 |
| 코드 간결성 | ✅ 매우 간결 | ❌ 직접 처리 필요 |
| 캐시 흐름 이해 | ❌ AOP로 감춰짐 | ✅ 명확히 보임 |
| 복잡한 캐시 구조 | ❌ 어려움 | ✅ 세밀한 제어 가능 |
| 실무 사용 예 | 단순 조회 (상품 상세 등) | TTL 제어, 조건부 캐싱 등 고급 케이스 |

> 단순한 `@Cacheable` 도 좋지만,
>
>
> 실무에서는 **캐시가 언제 저장되고 언제 무효화되는지**를 정확히 알아야 합니다.
>
> 그래서 이번 과제에서는 **직접 RedisTemplate을 사용해 캐시 흐름을 제어해보는 실습도 추천**합니다.
>

### 💡 실무 팁

- 캐시는 **정확한 데이터를 보장하지 않는다**는 점을 항상 인지해야 함
- 캐시 키 설계는 중요한 도메인 속성 기준으로 구체적으로 짜야 함
- 캐시를 적용할 때는 항상 **만약 캐시가 없으면?** 시나리오를 함께 고려
- 중요한 비즈니스 데이터에는 캐시보다 DB 정합성이 우선됨

---

## 🌾읽기 전용 구조 - Pre-aggregation

### 🚧 실무에서 겪는 문제

- 좋아요 수 정렬, 랭킹, 통계 페이지 등은 **매번 실시간으로 계산하면 느림**
- 실시간성보다 **빠른 응답**이 중요한 경우가 많음

### ✅ Pre-aggregation이란?

- **데이터를 미리 계산해서 저장해두는 구조**
- 조회 시점에는 가공 없이 바로 반환 가능

> e.g. 좋아요 수, 랭킹 정보, 카테고리별 상품 수 등
>

### 🧱 구현 방식 예시

| 방법 | 설명 | 예시 |
| --- | --- | --- |
| 조회용 테이블 | 자체적으로 만들어두는 별도 테이블 | `product_likes_view` 등 |
| 배치 or 이벤트 적재 | 주기적 or 발생 시점 집계 | Kafka, Scheduler 등 활용 |

### 💡 실무 팁 & 연결

- 실시간 정합성이 **꼭 필요하지 않은 데이터**라면, Pre-aggregation 을 고려하세요
- 이 전략은 후반 주차와 연결됩니다 → 그땐 Materialized View나 Batch 적재를 직접 다뤄볼 예정이에요!

<aside>
📚

**References**

</aside>

| 구분 | 링크 |
| --- | --- |
| 🔍 MySQL 인덱스 튜닝 | [쿼리 튜닝과 인덱스 최적화](https://wikidocs.net/226253) |
| 📖 인덱스 정렬 방향 이슈 | [카카오 테크 - MySQL 방향별 인덱스](https://tech.kakao.com/posts/351) |
| ⚙ Spring Cache 개요 | [Spring - Caching](https://docs.spring.io/spring-boot/reference/io/caching.html) |
| 🧰 RedisTemplate 사용법 | [Baeldung -Spring Data Redis](https://www.baeldung.com/spring-data-redis-tutorial) |
| 🧠 캐시 전략 비교 | [Inpa Dev - REDIS 캐시 설계 전략 지침](https://inpa.tistory.com/entry/REDIS-%F0%9F%93%9A-%EC%BA%90%EC%8B%9CCache-%EC%84%A4%EA%B3%84-%EC%A0%84%EB%9E%B5-%EC%A7%80%EC%B9%A8-%EC%B4%9D%EC%A0%95%EB%A6%AC) |
| 🌾 Materialized View 소개 | [AWS - Materialized View](https://aws.amazon.com/ko/what-is/materialized-view/) |

<aside>
🌟

**Next Week Preview**

</aside>

> **PG (Payment Gateway) 가 죽었더니 주문도 실패하네?**
>
>
> 이제 우리 시스템은 **외부 시스템(PG)** 과 통신하게 됩니다.
>
> 다음 주에는 외부 의존성으로부터 시스템을 보호하기 위한 **Resilience 설계 전략**을 학습합니다.
>
> - 새로운 요구사항: **PG 기반 결제 방식 도입**
> - **장애나 지연 상황**에서도 서비스가 무너지지 않도록
> - **서킷 브레이커, 타임아웃, 재시도, 폴백** 등의 회복 전략
> - **실패 감지와 사용자 응답 설계**, **전체 흐름이 망가지지 않게 분리하는 방법**