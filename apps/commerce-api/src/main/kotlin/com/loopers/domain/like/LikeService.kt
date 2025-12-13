package com.loopers.domain.like

import com.loopers.domain.like.event.ProductLikedEvent
import com.loopers.domain.like.event.ProductUnlikedEvent
import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class LikeService(
    private val likeRepository: LikeRepository,
    private val memberRepository: MemberRepository,
    private val productRepository: ProductRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional
    fun addLike(memberId: String, productId: Long): Like {
        val member = memberRepository.findByMemberIdOrThrow(memberId)

        // 이미 좋아요가 있으면 기존 것 반환 (멱등성 보장)
        val existingLike = likeRepository.findByMemberIdAndProductId(member.id, productId)
        if (existingLike != null) {
            return existingLike
        }

        try {
            // 좋아요 저장
            val product = productRepository.findByIdOrThrow(productId)
            val like = Like.of(member, product)
            val savedLike = likeRepository.save(like)

            // 이벤트 발행 (집계는 이벤트 핸들러에서)
            publishProductLikedEvent(savedLike, member, productId)

            return savedLike
        } catch (e: DataIntegrityViolationException) {
            // 동시 요청으로 인한 중복 insert 시도 - 기존 데이터 반환
            return likeRepository.findByMemberIdAndProductId(member.id, productId)
                ?: throw CoreException(ErrorType.INTERNAL_ERROR, "동시 요청 처리 중 일시적 오류 발생")
        }

    }

    private fun publishProductLikedEvent(
        savedLike: Like,
        member: Member,
        productId: Long,
    ) {
        // 좋아요 집계를 위한 이벤트
        eventPublisher.publishEvent(
            ProductLikedEvent(
                likeId = savedLike.id,
                memberId = member.memberId.value,
                productId = productId,
                likedAt = Instant.now(),
            ),
        )
    }

    @Transactional
    fun cancelLike(memberId: String, productId: Long) {
        val member = memberRepository.findByMemberIdOrThrow(memberId)

        // 좋아요가 없으면 그냥 리턴 (멱등성 보장)
        val like = likeRepository.findByMemberIdAndProductId(member.id, productId)
            ?: return

        // 좋아요 삭제
        likeRepository.deleteByMemberIdAndProductId(member.id, productId)

        // 이벤트 발행 (집계는 이벤트 핸들러에서)
        eventPublisher.publishEvent(
            ProductUnlikedEvent(
                productId = productId,
                memberId = member.memberId.value,
                unlikedAt = Instant.now()
            )
        )
    }

    @Transactional(readOnly = true)
    fun getMyLikes(memberId: String, pageable: Pageable): Page<Like> {
        val member = memberRepository.findByMemberIdOrThrow(memberId)
        return likeRepository.findByMemberId(member.id, pageable)
    }
}
