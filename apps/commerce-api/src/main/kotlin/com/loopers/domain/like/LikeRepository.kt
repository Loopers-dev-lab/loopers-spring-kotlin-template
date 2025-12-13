package com.loopers.domain.like

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface LikeRepository {
    fun save(like: Like): Like
    fun findByMemberIdAndProductId(memberId: Long, productId: Long): Like?
    fun deleteByMemberIdAndProductId(memberId: Long, productId: Long)
    fun findByMemberId(memberId: Long, pageable: Pageable): Page<Like>
    fun existsByMemberIdAndProductId(memberId: Long, productId: Long): Boolean
    fun delete(like: Like)
}
