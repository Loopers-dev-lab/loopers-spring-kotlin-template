package com.loopers.fixtures

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.domain.product.Product
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * Product 엔티티를 위한 테스트 픽스처 함수
 *
 * @param id 상품 ID (null인 경우 ID를 설정하지 않음)
 * @param name 상품명 (기본값: "테스트 상품")
 * @param price 가격 (기본값: 100000원)
 * @param brand 브랜드
 * @param createdAt 생성 시점 (기본값: 현재 시간, id가 null이면 무시됨)
 * @param updatedAt 수정 시점 (기본값: 현재 시간, id가 null이면 무시됨)
 * @return 테스트용 Product 인스턴스
 */
fun createTestProduct(
    id: Long? = null,
    name: String = "테스트 상품",
    price: BigDecimal = BigDecimal("100000"),
    brand: Brand,
    createdAt: ZonedDateTime = ZonedDateTime.now(),
    updatedAt: ZonedDateTime = ZonedDateTime.now(),
): Product {
    val product = Product(
        name = name,
        price = Price(price, Currency.KRW),
        brand = brand,
    )
    return if (id != null) {
        product.withId(id, createdAt, updatedAt)
    } else {
        product
    }
}
