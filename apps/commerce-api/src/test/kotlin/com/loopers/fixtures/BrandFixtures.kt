package com.loopers.fixtures

import com.loopers.domain.brand.Brand
import java.time.ZonedDateTime

/**
 * Brand 엔티티를 위한 테스트 픽스처 함수
 *
 * @param id 브랜드 ID
 * @param name 브랜드명 (기본값: "테스트 브랜드")
 * @param description 브랜드 설명 (기본값: "테스트 설명")
 * @param createdAt 생성 시점 (기본값: 현재 시간)
 * @param updatedAt 수정 시점 (기본값: 현재 시간)
 * @return 테스트용 Brand 인스턴스
 */
fun createTestBrand(
    id: Long,
    name: String = "테스트 브랜드",
    description: String? = "테스트 설명",
    createdAt: ZonedDateTime = ZonedDateTime.now(),
    updatedAt: ZonedDateTime = ZonedDateTime.now(),
): Brand {
    return Brand(
        name = name,
        description = description,
    ).withId(id, createdAt, updatedAt)
}
