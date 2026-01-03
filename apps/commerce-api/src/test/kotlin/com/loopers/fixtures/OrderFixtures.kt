package com.loopers.fixtures

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Order 엔티티를 위한 테스트 픽스처 함수
 *
 * @param id 주문 ID
 * @param userId 사용자 ID
 * @param items 주문 항목 리스트
 * @param createdAt 생성 시점 (기본값: 현재 시간)
 * @param updatedAt 수정 시점 (기본값: 현재 시간)
 * @return 테스트용 Order 인스턴스
 */
fun createTestOrder(
    id: Long,
    userId: Long,
    items: List<OrderItem> = listOf(
        OrderItem(
            productId = 100L,
            productName = "테스트 상품",
            brandId = 1L,
            brandName = "테스트 브랜드",
            brandDescription = null,
            quantity = 1,
            priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
        ),
    ),
    createdAt: LocalDateTime = LocalDateTime.now(),
    updatedAt: LocalDateTime = LocalDateTime.now(),
): Order = Order(
    userId = userId,
    items = items,
).withId(id, createdAt, updatedAt)
