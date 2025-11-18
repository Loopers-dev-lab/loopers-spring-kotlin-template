package com.loopers.infrastructure.like

import com.loopers.domain.like.Like
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface LikeJpaRepository : JpaRepository<Like, Long> {
    fun findByMemberIdAndProductId(memberId: Long, productId: Long): Like?
    fun deleteByMemberIdAndProductId(memberId: Long, productId: Long)
    fun findByMemberId(memberId: Long, pageable: Pageable): Page<Like>
    fun existsByMemberIdAndProductId(memberId: Long, productId: Long): Boolean
}
