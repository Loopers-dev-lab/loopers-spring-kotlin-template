package com.loopers.fixtures

import com.loopers.domain.brand.Brand
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

private val brandNameCounter = AtomicLong(0)

/**
 * Brand 엔티티를 위한 테스트 픽스처 함수
 *
 * @param id 브랜드 ID (null인 경우 ID를 설정하지 않음)
 * @param name 브랜드명 (null인 경우 자동 생성된 유니크한 이름 사용)
 * @param description 브랜드 설명 (기본값: "테스트 설명")
 * @param createdAt 생성 시점 (기본값: 현재 시간, id가 null이면 무시됨)
 * @param updatedAt 수정 시점 (기본값: 현재 시간, id가 null이면 무시됨)
 * @return 테스트용 Brand 인스턴스
 */
fun createTestBrand(
    id: Long? = null,
    name: String? = null,
    description: String? = "테스트 설명",
    createdAt: LocalDateTime = LocalDateTime.now(),
    updatedAt: LocalDateTime = LocalDateTime.now(),
): Brand {
    val brandName = name ?: "테스트브랜드${brandNameCounter.incrementAndGet()}"
    val brand = Brand(
        name = brandName,
        description = description,
    )
    return if (id != null) {
        brand.withId(id, createdAt, updatedAt)
    } else {
        brand
    }
}
