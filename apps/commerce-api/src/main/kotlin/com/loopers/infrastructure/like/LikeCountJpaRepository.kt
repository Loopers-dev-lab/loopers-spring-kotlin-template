package com.loopers.infrastructure.like

import com.loopers.domain.like.entity.LikeCount
import com.loopers.domain.like.vo.LikeTarget.Type
import org.springframework.data.jpa.repository.JpaRepository

interface LikeCountJpaRepository : JpaRepository<LikeCount, Long> {
    @Suppress("FunctionName")
    fun findByTarget_TargetIdAndTarget_Type(targetTargetId: Long, type: Type): LikeCount?

    @Suppress("FunctionName")
    fun findAllByTarget_TargetIdInAndTarget_Type(targetTargetIds: List<Long>, type: Type): List<LikeCount>
}
