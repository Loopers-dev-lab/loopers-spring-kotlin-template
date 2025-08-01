package com.loopers.domain.like

import com.loopers.domain.like.entity.LikeCount
import com.loopers.domain.like.vo.LikeTarget.Type

interface LikeCountRepository {
    fun findCountByTargetIdAndType(targetId: Long, type: Type): LikeCount

    fun findAllCountByTargetIdAndType(targetIds: List<Long>, type: Type): List<LikeCount>

    fun save(likeCount: LikeCount): LikeCount
}
