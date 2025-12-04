# Error Handling Guide

에러 처리 패턴 가이드입니다.

---

## 기본 원칙

- **기본**: `CoreException` + `ErrorType` → GlobalExceptionHandler에서 처리
- **비즈니스 분기 필요 시**: sealed class 결과 반환

---

## CoreException 패턴 (기본)

대부분의 케이스에서 사용합니다.

### Entity Validation

```kotlin
class PointAccount(
    val id: Long,
    var balance: Money,
) {
    fun deduct(amount: Money) {
        if (amount <= Money.ZERO_KRW) {
            throw CoreException(ErrorType.BAD_REQUEST, "차감은 양수여야 합니다.")
        }
        if (this.balance < amount) {
            throw CoreException(ErrorType.BAD_REQUEST, "포인트가 부족합니다.")
        }
        this.balance = this.balance.minus(amount)
    }
}
```

### Service - Not Found 처리

Elvis operator와 함께 사용합니다.

```kotlin
@Service
class PointService(private val pointAccountRepository: PointAccountRepository) {

    fun deduct(userId: Long, amount: Money): PointAccount {
        val pointAccount = pointAccountRepository.findByUserIdWithLock(userId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[id = $userId] 유저를 찾을 수 없습니다.",
            )
        pointAccount.deduct(amount)
        return pointAccountRepository.save(pointAccount)
    }
}
```

---

## Sealed Class 패턴 (비즈니스 분기 필요 시)

Caller가 결과별로 다른 후속 처리가 필요한 경우에만 사용합니다.

### 결과 정의

```kotlin
sealed class UseCouponResult {
    data class Success(val discount: Money) : UseCouponResult()
    data class AlreadyUsed(val usedAt: ZonedDateTime) : UseCouponResult()
    data class Expired(val expiredAt: ZonedDateTime) : UseCouponResult()
    data class NotOwned(val ownerId: Long) : UseCouponResult()
}
```

### Service에서 반환

```kotlin
@Service
class CouponUsageService(private val issuedCouponRepository: IssuedCouponRepository) {

    fun useCoupon(userId: Long, couponId: Long): UseCouponResult {
        val issuedCoupon = issuedCouponRepository.findByCouponId(couponId)
            ?: return UseCouponResult.NotOwned(userId)

        if (issuedCoupon.isExpired()) {
            return UseCouponResult.Expired(issuedCoupon.expiredAt)
        }
        if (issuedCoupon.isUsed()) {
            return UseCouponResult.AlreadyUsed(issuedCoupon.usedAt!!)
        }

        issuedCoupon.use()
        return UseCouponResult.Success(issuedCoupon.calculateDiscount())
    }
}
```

### Caller에서 분기 처리

```kotlin
@Component
class OrderFacade(private val couponUsageService: CouponUsageService) {

    fun placeOrderWithCoupon(userId: Long, couponId: Long, order: Order) {
        when (val result = couponUsageService.useCoupon(userId, couponId)) {
            is UseCouponResult.Success -> {
                order.applyDiscount(result.discount)
            }
            is UseCouponResult.AlreadyUsed -> {
                // 이미 사용된 쿠폰 → 쿠폰 없이 주문 진행
                logger.info("쿠폰이 이미 사용됨, 쿠폰 없이 진행")
            }
            is UseCouponResult.Expired -> {
                // 만료된 쿠폰 → 다른 쿠폰 추천
                suggestAlternativeCoupons(userId)
            }
            is UseCouponResult.NotOwned -> {
                // 비정상 접근 → 로깅 후 진행
                logger.warn("소유하지 않은 쿠폰 사용 시도")
            }
        }
    }
}
```

---

## Null Safety 패턴

### Elvis Operator + Throw

```kotlin
val user = userRepository.findById(userId)
    ?: throw CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다")
```

### Safe Call + let

```kotlin
order.couponId?.let { couponId ->
    couponService.useCoupon(couponId)
}
```

### Non-nullable 타입 기본 사용

빈 컬렉션은 `emptyList()` 사용

```kotlin
// Good
fun getOrders(userId: Long): List<Order> {
    return orderRepository.findByUserId(userId)
}
```

---

## 관련 문서

| 주제 | 문서 |
|------|------|
| 레이어별 구현 | [layer-guide.md](layer-guide.md) |
| 동시성 제어 | [concurrency-guide.md](concurrency-guide.md) |
