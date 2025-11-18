package com.loopers.domain.like

import com.loopers.domain.member.MemberRepository
import com.loopers.domain.product.ProductRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeService(
    private val likeRepository: LikeRepository,
    private val memberRepository: MemberRepository,
    private val productRepository: ProductRepository,
) {

    @Transactional
    fun addLike(memberId: Long, productId: Long): Like {
        // 이미 좋아요가 있으면 기존 것 반환
        val existingLike = likeRepository.findByMemberIdAndProductId(memberId, productId)
        if (existingLike != null) {
            return existingLike
        }

        val member = memberRepository.findByIdOrThrow(memberId)
        val product = productRepository.findByIdOrThrow(productId)

        val like = Like.of(member, product)
        product.increaseLikesCount()

        return likeRepository.save(like)
    }

    @Transactional
    fun cancelLike(memberId: Long, productId: Long) {
        // 좋아요가 없으면 그냥 리턴
        val like = likeRepository.findByMemberIdAndProductId(memberId, productId)
            ?: return

        val product = productRepository.findByIdOrThrow(productId)
        product.decreaseLikesCount()

        likeRepository.deleteByMemberIdAndProductId(memberId, productId)
    }

    @Transactional(readOnly = true)
    fun getMyLikes(memberId: Long, pageable: Pageable): Page<Like> {
        return likeRepository.findByMemberId(memberId, pageable)
    }
}
