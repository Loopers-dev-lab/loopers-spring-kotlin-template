package com.loopers.domain.like

import com.loopers.application.like.LikeInfo
import com.loopers.domain.like.event.ProductLikedEvent
import com.loopers.domain.like.event.ProductUnlikedEvent
import com.loopers.domain.member.MemberRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun addLike(memberId: String, productId: Long): LikeInfo {
        val member = memberRepository.findByMemberIdOrThrow(memberId)

        // 이미 좋아요가 있으면 기존 것 반환 (멱등성 보장)
        val existingLike = likeRepository.findByMemberIdAndProductId(member.id, productId)
        if (existingLike != null) {
            val product = productRepository.findByIdOrThrow(productId)
            return LikeInfo.from(existingLike, product, member.memberId.value)
        }

        return try {
            val product = productRepository.findByIdOrThrow(productId)
            val like = Like.of(member.id, productId)
            val savedLike = likeRepository.save(like)

            // 이벤트 발행
            eventPublisher.publishEvent(
                ProductLikedEvent(
                    likeId = savedLike.id,
                    memberId = member.memberId.value,
                    productId = productId,
                    likedAt = Instant.now(),
                ),
            )

            LikeInfo.from(savedLike, product, member.memberId.value)
        } catch (e: DataIntegrityViolationException) {
            // 동시 요청으로 인한 중복 insert 시도 - 기존 데이터 반환
            logger.debug("동시 좋아요 요청으로 인한 중복 insert 시도: memberId={}, productId={}", member.memberId.value, productId, e)
            val like = (likeRepository.findByMemberIdAndProductId(member.id, productId)
                ?: throw CoreException(ErrorType.INTERNAL_ERROR, "좋아요 데이터 조회 실패 - DB 상태 확인 필요"))
            val product = productRepository.findByIdOrThrow(productId)
            LikeInfo.from(like, product, member.memberId.value)
        }
    }

    @Transactional
    fun cancelLike(memberId: String, productId: Long) {
        val member = memberRepository.findByMemberIdOrThrow(memberId)

        // 좋아요가 없으면 그냥 리턴 (멱등성 보장)
        val like = likeRepository.findByMemberIdAndProductId(member.id, productId)
            ?: return

        // 좋아요 삭제 (이미 조회한 엔티티를 직접 삭제)
        likeRepository.delete(like)

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
    fun getMyLikes(memberId: String, pageable: Pageable): Page<LikeInfo> {
        val member = memberRepository.findByMemberIdOrThrow(memberId)
        val likes = likeRepository.findByMemberId(member.id, pageable)

        val productIds = likes.content.map { it.productId }
        val products = productRepository.findAllByIdIn(productIds)
            .associateBy { it.id }


        return likes.map { like ->
            val product = products[like.productId]
                ?: throw CoreException(ErrorType.PRODUCT_NOT_FOUND)
            LikeInfo.from(like, product, member.memberId.value)
        }
    }
}
