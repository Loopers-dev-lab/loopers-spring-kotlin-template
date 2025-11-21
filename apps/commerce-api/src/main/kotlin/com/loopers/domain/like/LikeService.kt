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
    fun addLike(memberId: String, productId: Long): Like {
        val member = memberRepository.findByMemberIdOrThrow(memberId)

        // 이미 좋아요가 있으면 기존 것 반환 (멱등성 보장)
        val existingLike = likeRepository.findByMemberIdAndProductId(member.id, productId)
        if (existingLike != null) {
            return existingLike
        }

        // 비관적 락 적용 - likesCount 동시성 제어
        val product = productRepository.findByIdWithLockOrThrow(productId)

        val like = Like.of(member, product)
        product.increaseLikesCount()

        return likeRepository.save(like)
    }

    @Transactional
    fun cancelLike(memberId: String, productId: Long) {
        val member = memberRepository.findByMemberIdOrThrow(memberId)

        // 좋아요가 없으면 그냥 리턴 (멱등성 보장)
        val like = likeRepository.findByMemberIdAndProductId(member.id, productId)
            ?: return

        // 비관적 락 적용 - likesCount 동시성 제어
        val product = productRepository.findByIdWithLockOrThrow(productId)
        product.decreaseLikesCount()

        likeRepository.deleteByMemberIdAndProductId(member.id, productId)
    }

    @Transactional(readOnly = true)
    fun getMyLikes(memberId: String, pageable: Pageable): Page<Like> {
        val member = memberRepository.findByMemberIdOrThrow(memberId)
        return likeRepository.findByMemberId(member.id, pageable)
    }
}
