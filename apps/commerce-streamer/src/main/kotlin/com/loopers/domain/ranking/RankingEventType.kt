package com.loopers.domain.ranking

/**
 * RankingEventType - 랭킹 집계를 위한 이벤트 타입
 *
 * - VIEW: 상품 조회
 * - LIKE_CREATED: 좋아요 생성
 * - LIKE_CANCELED: 좋아요 취소
 * - ORDER_PAID: 주문 결제 완료
 */
enum class RankingEventType {
    VIEW,
    LIKE_CREATED,
    LIKE_CANCELED,
    ORDER_PAID,
}
