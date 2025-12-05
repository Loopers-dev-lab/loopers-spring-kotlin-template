# PG Simulator API 문서

결제 모듈 과제용 PG 시뮬레이터 API 명세서입니다.

## 공통 사항

### Base URL

```
{{pg-simulator}}/api/v1/payments
```

### 필수 헤더

| 헤더 | 필수 | 설명 |
|------|------|------|
| `X-USER-ID` | ✅ | 사용자 ID (없으면 400 에러) |
| `Content-Type` | ✅ | `application/json` (POST 요청 시) |

### 응답 형식

모든 API는 아래 형식으로 응답합니다.

```json
{
  "meta": {
    "result": "SUCCESS | FAIL",
    "errorCode": null | "ERROR_CODE",
    "message": null | "에러 메시지"
  },
  "data": { ... }
}
```

### ⚠️ 시뮬레이터 특성 (과제 시 고려 필요)

- **40% 확률로 요청 실패** - 불안정한 서버 시뮬레이션
- **100~500ms 랜덤 지연** - 네트워크 지연 시뮬레이션
- **재시도 로직 구현 권장**

---

## API 명세

### 1. 결제 요청

새로운 결제 트랜잭션을 생성합니다.

```
POST /api/v1/payments
```

#### Request

```http
POST {{pg-simulator}}/api/v1/payments
X-USER-ID: 135135
Content-Type: application/json

{
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451",
  "amount": 5000,
  "callbackUrl": "http://localhost:8080/api/v1/examples/callback"
}
```

#### Request Body

| 필드 | 타입 | 필수 | 설명 | 검증 규칙 |
|------|------|------|------|-----------|
| `orderId` | String | ✅ | 주문 ID | 6자 이상 문자열 |
| `cardType` | String | ✅ | 카드 종류 | `SAMSUNG`, `KB`, `HYUNDAI` 중 하나 |
| `cardNo` | String | ✅ | 카드 번호 | `xxxx-xxxx-xxxx-xxxx` 형식 (숫자 4자리-4자리-4자리-4자리) |
| `amount` | Long | ✅ | 결제 금액 | 양의 정수 (0 초과) |
| `callbackUrl` | String | ✅ | 결제 완료 콜백 URL | `http://localhost:8080`으로 시작해야 함 |

#### Response (성공)

```json
{
  "meta": {
    "result": "SUCCESS",
    "errorCode": null,
    "message": null
  },
  "data": {
    "transactionKey": "20250816:TR:9577c5",
    "status": "PENDING",
    "reason": null
  }
}
```

#### Response Fields

| 필드 | 타입 | 설명 |
|------|------|------|
| `transactionKey` | String | 결제 트랜잭션 고유 키 (결제 조회 시 사용) |
| `status` | String | 트랜잭션 상태 (`PENDING`, `SUCCESS`, `FAILED`) |
| `reason` | String? | 실패 사유 (실패 시에만 값 존재) |

---

### 2. 결제 정보 확인 (단건)

트랜잭션 키로 결제 상세 정보를 조회합니다.

```
GET /api/v1/payments/{transactionKey}
```

#### Request

```http
GET {{pg-simulator}}/api/v1/payments/20250816:TR:9577c5
X-USER-ID: 135135
```

#### Path Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `transactionKey` | String | ✅ | 결제 요청 시 발급받은 트랜잭션 키 |

#### Response (성공)

```json
{
  "meta": {
    "result": "SUCCESS",
    "errorCode": null,
    "message": null
  },
  "data": {
    "transactionKey": "20250816:TR:9577c5",
    "orderId": "1351039135",
    "cardType": "SAMSUNG",
    "cardNo": "1234-5678-9814-1451",
    "amount": 5000,
    "status": "SUCCESS",
    "reason": null
  }
}
```

#### Response Fields

| 필드 | 타입 | 설명 |
|------|------|------|
| `transactionKey` | String | 결제 트랜잭션 고유 키 |
| `orderId` | String | 주문 ID |
| `cardType` | String | 카드 종류 |
| `cardNo` | String | 카드 번호 |
| `amount` | Long | 결제 금액 |
| `status` | String | 트랜잭션 상태 |
| `reason` | String? | 실패 사유 |

---

### 3. 주문별 결제 정보 조회

주문 ID로 해당 주문에 연결된 모든 결제 트랜잭션을 조회합니다.

```
GET /api/v1/payments?orderId={orderId}
```

#### Request

```http
GET {{pg-simulator}}/api/v1/payments?orderId=1351039135
X-USER-ID: 135135
```

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `orderId` | String | ✅ | 주문 ID |

#### Response (성공)

```json
{
  "meta": {
    "result": "SUCCESS",
    "errorCode": null,
    "message": null
  },
  "data": {
    "orderId": "1351039135",
    "transactions": [
      {
        "transactionKey": "20250816:TR:9577c5",
        "status": "SUCCESS",
        "reason": null
      }
    ]
  }
}
```

#### Response Fields

| 필드 | 타입 | 설명 |
|------|------|------|
| `orderId` | String | 주문 ID |
| `transactions` | Array | 해당 주문의 트랜잭션 목록 |
| `transactions[].transactionKey` | String | 트랜잭션 키 |
| `transactions[].status` | String | 트랜잭션 상태 |
| `transactions[].reason` | String? | 실패 사유 |

---

## Enum 정의

### CardType (카드 종류)

| 값 | 설명 |
|------|------|
| `SAMSUNG` | 삼성카드 |
| `KB` | KB국민카드 |
| `HYUNDAI` | 현대카드 |

### TransactionStatus (트랜잭션 상태)

| 값 | 설명 |
|------|------|
| `PENDING` | 결제 처리 중 |
| `SUCCESS` | 결제 성공 |
| `FAILED` | 결제 실패 |

---

## 에러 응답

### 에러 응답 형식

```json
{
  "meta": {
    "result": "FAIL",
    "errorCode": "BAD_REQUEST",
    "message": "에러 메시지"
  },
  "data": null
}
```

### 주요 에러 케이스

| 상황 | errorCode | message 예시 |
|------|-----------|--------------|
| X-USER-ID 헤더 누락 | `BAD_REQUEST` | 유저 ID 헤더는 필수입니다. |
| 주문 ID 6자 미만 | `BAD_REQUEST` | 주문 ID는 6자리 이상 문자열이어야 합니다. |
| 카드번호 형식 오류 | `BAD_REQUEST` | 카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다. |
| 결제금액 0 이하 | `BAD_REQUEST` | 결제금액은 양의 정수여야 합니다. |
| 콜백 URL 형식 오류 | `BAD_REQUEST` | 콜백 URL 은 http://localhost:8080 로 시작해야 합니다. |
| 서버 불안정 (40% 확률) | `INTERNAL_ERROR` | 현재 서버가 불안정합니다. 잠시 후 다시 시도해주세요. |

---

## 과제 구현 시 참고사항

1. **재시도 로직 필수**: 40% 실패율로 인해 Retry 패턴 구현 권장
2. **Timeout 설정**: 최대 500ms 지연이 발생하므로 적절한 timeout 설정 필요
3. **콜백 URL 제약**: `http://localhost:8080`으로 시작하는 URL만 허용
4. **트랜잭션 상태 확인**: 결제 요청 후 `PENDING` 상태로 시작하며, 이후 상태 변경됨
