package com.loopers.domain.like

import com.loopers.domain.event.LikeAddedEvent
import com.loopers.domain.event.LikeRemovedEvent
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeService(
    private val likeRepository: LikeRepository,
    private val productRepository: ProductRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * 좋아요를 등록한다
     * UniqueConstraint를 활용하여 멱등성 보장
     * 집계 처리는 이벤트 핸들러에서 비동기로 처리
     */
    @Transactional
    fun addLike(userId: Long, productId: Long) {
        // 이미 존재하는지 확인
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }

        // 상품 존재 여부 확인
        if (!productRepository.existsById(productId)) {
            throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
        }

        // 저장 시도
        val like = Like(userId = userId, productId = productId)
        likeRepository.save(like)

        // 좋아요 추가 이벤트 발행 (집계 및 캐시 무효화는 이벤트 핸들러에서 처리)
        eventPublisher.publishEvent(LikeAddedEvent(userId = userId, productId = productId))
    }

    @Transactional
    fun removeLike(userId: Long, productId: Long) {
        // 상품 존재 여부 확인
        if (!productRepository.existsById(productId)) {
            throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
        }

        // 삭제 시도 및 삭제된 행 수 확인
        val deletedCount = likeRepository.deleteByUserIdAndProductId(userId, productId)

        // 실제로 삭제된 경우에만 이벤트 발행
        if (deletedCount > 0) {
            // 좋아요 제거 이벤트 발행 (집계 및 캐시 무효화는 이벤트 핸들러에서 처리)
            eventPublisher.publishEvent(LikeRemovedEvent(userId = userId, productId = productId))
        }
    }
}
