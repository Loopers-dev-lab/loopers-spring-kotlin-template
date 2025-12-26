package com.loopers.domain.ranking

import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * MutableCounts - 스레드 안전한 카운트 누적기
 *
 * - ConcurrentHashMap의 computeIfAbsent와 함께 사용
 * - AtomicLong/AtomicReference로 스레드 안전한 카운트 누적 보장
 * - toSnapshot()으로 불변 스냅샷 생성 가능
 */
class MutableCounts {
    private val views = AtomicLong(0)
    private val likes = AtomicLong(0)
    private val orderCount = AtomicLong(0)
    private val orderAmount = AtomicReference(BigDecimal.ZERO)

    /**
     * 이벤트 타입에 따라 해당 카운터 증가/감소
     * - VIEW: views 증가
     * - LIKE_CREATED: likes 증가
     * - LIKE_CANCELED: likes 감소
     * - ORDER_PAID: orderCount 증가
     */
    fun increment(eventType: RankingEventType) {
        when (eventType) {
            RankingEventType.VIEW -> views.incrementAndGet()
            RankingEventType.LIKE_CREATED -> likes.incrementAndGet()
            RankingEventType.LIKE_CANCELED -> likes.decrementAndGet()
            RankingEventType.ORDER_PAID -> orderCount.incrementAndGet()
        }
    }

    /**
     * 주문 금액 누적 (스레드 안전)
     */
    fun addOrderAmount(amount: BigDecimal) {
        orderAmount.updateAndGet { it.add(amount) }
    }

    /**
     * 현재 상태의 불변 스냅샷 반환
     * - 스냅샷 생성 후 원본 MutableCounts는 계속 변경 가능
     */
    fun toSnapshot(): CountSnapshot {
        return CountSnapshot(
            views = views.get(),
            likes = likes.get(),
            orderCount = orderCount.get(),
            orderAmount = orderAmount.get(),
        )
    }

    /**
     * 현재 views 값 조회 (테스트용)
     */
    fun getViews(): Long = views.get()

    /**
     * 현재 likes 값 조회 (테스트용)
     */
    fun getLikes(): Long = likes.get()

    /**
     * 현재 orderCount 값 조회 (테스트용)
     */
    fun getOrderCount(): Long = orderCount.get()

    /**
     * 현재 orderAmount 값 조회 (테스트용)
     */
    fun getOrderAmount(): BigDecimal = orderAmount.get()
}
