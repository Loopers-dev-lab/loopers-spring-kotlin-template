package com.loopers.domain.event.coupon

import com.loopers.domain.event.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * 쿠폰 사용 이벤트
 * - 주문 생성 시 쿠폰 사용 후 발행
 * - order-events 토픽으로 발행 (key=orderId)
 */
data class CouponUsedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "COUPON_USED",
    override val aggregateId: Long, // orderId (partitionKey)
    override val occurredAt: Instant = Instant.now(),

    val orderId: Long,
    val memberId: String,
    val couponId: Long,
    val memberCouponId: Long, // 사용된 MemberCoupon ID
    val discountAmount: Long, // 할인 금액
    val usedAt: Instant = Instant.now(),
) : DomainEvent
