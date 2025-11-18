package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class LikeRepositoryImpl(
    private val likeJpaRepository: LikeJpaRepository,
) : LikeRepository {
    override fun save(like: Like): Like {
        return likeJpaRepository.save(like)
    }

    override fun findByMemberIdAndProductId(memberId: Long, productId: Long): Like? {
        return likeJpaRepository.findByMemberIdAndProductId(memberId, productId)
    }

    override fun deleteByMemberIdAndProductId(memberId: Long, productId: Long) {
        return likeJpaRepository.deleteByMemberIdAndProductId(memberId, productId)
    }

    override fun findByMemberId(
        memberId: Long,
        pageable: Pageable,
    ): Page<Like> {
        return likeJpaRepository.findByMemberId(memberId, pageable)
    }

    override fun existsByMemberIdAndProductId(memberId: Long, productId: Long): Boolean {
        return likeJpaRepository.existsByMemberIdAndProductId(memberId, productId)
    }

}
