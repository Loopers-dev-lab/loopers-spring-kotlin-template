package com.loopers.fixtures

import com.loopers.domain.like.Like
import java.time.ZonedDateTime

/**
 * Like 엔티티를 위한 테스트 픽스처 함수
 *
 * @param id 좋아요 ID
 * @param userId 사용자 ID
 * @param productId 상품 ID
 * @param createdAt 생성 시점 (기본값: 현재 시간)
 * @param updatedAt 수정 시점 (기본값: 현재 시간)
 * @return 테스트용 Like 인스턴스
 */
fun createTestLike(
    id: Long,
    userId: Long,
    productId: Long,
    createdAt: ZonedDateTime = ZonedDateTime.now(),
    updatedAt: ZonedDateTime = ZonedDateTime.now(),
): Like = Like(
        userId = userId,
        productId = productId,
    ).withId(id, createdAt, updatedAt)
