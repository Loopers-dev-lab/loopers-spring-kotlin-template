package com.loopers.application.like

import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val productRepository: ProductRepository,
) {
    @Transactional
    fun addLike(userId: Long, productId: Long) {
        if (!productRepository.existsById(productId)) {
            throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
        }

        try {
            likeService.addLike(userId, productId)
        } catch (e: DataIntegrityViolationException) {
            // UniqueConstraint 위반 - 이미 좋아요가 존재함 (멱등성 보장)
            // 정상적인 케이스이므로 로그만 남기고 조용히 처리
            logger.debug("좋아요가 이미 존재합니다 (userId=$userId, productId=$productId)")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LikeFacade::class.java)
    }

    @Transactional
    fun removeLike(userId: Long, productId: Long) {
        if (!productRepository.existsById(productId)) {
            throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
        }

        likeService.removeLike(userId, productId)
    }
}
