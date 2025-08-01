package com.loopers.domain.like

import com.loopers.domain.like.dto.criteria.LikeCriteria
import com.loopers.domain.like.entity.Like
import com.loopers.domain.like.vo.LikeTarget.Type
import org.springframework.data.domain.Page

interface LikeRepository {
    fun find(userId: Long, targetId: Long, type: Type): Like?

    fun findAll(criteria: LikeCriteria.FindAll): Page<Like>

    fun save(like: Like): Like

    fun delete(like: Like)
}
